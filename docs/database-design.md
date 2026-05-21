# Database Design

Every choice below is deliberate. If you find one inconvenient, talk to the team before changing it — most of these are load-bearing.

---

## 1. Identifier Strategy

Every business table has **two** IDs:

```sql
id          BIGSERIAL    PRIMARY KEY,                                -- internal
public_id   UUID         NOT NULL DEFAULT uuid_generate_v7() UNIQUE  -- external
```

| Use | Type | When |
|---|---|---|
| `id` (BIGSERIAL) | `BIGINT` | Internal joins, FKs **within the same database**, indexes. Tight, sequential, cache-friendly. |
| `public_id` (UUID v7) | `UUID` | API response, URL paths, cross-service references, anywhere a client sees an ID. |

### Why UUID v7

- **Time-ordered**: first 48 bits are a millisecond timestamp → naturally sorted by creation time.
- **Index-friendly**: unlike v4, doesn't cause B-tree fragmentation.
- **Hides count**: clients can't iterate / scrape based on monotonic IDs.
- **Distributed-safe**: when we shard or split services, no coordination needed.

Postgres 17 ships `uuid_generate_v7()` natively. We have a polyfill in `V2__init_helpers.sql` for Postgres 16.

### Cross-service references

```sql
-- order_db.orders
user_public_id  UUID    -- → catalog_db.users.public_id (no FK, no JOIN)
```

This is the **only** form a cross-service reference may take. See [architecture.md](architecture.md#3-the-database-per-service-rule).

---

## 2. Money

```sql
price_amount    BIGINT  NOT NULL CHECK (price_amount >= 0),  -- smallest unit (satang for THB)
price_currency  CHAR(3) NOT NULL                              -- ISO 4217
```

### Rules

- **Always `BIGINT` for amounts.** Never `DOUBLE`, never `FLOAT`, never `NUMERIC` unless you have a strong reason.
- **Store the smallest unit** of the currency:
  - THB → satang (multiply display by 100)
  - USD → cents
  - JPY → yen (no fractional unit — multiplier = 1)
- **Always pair with a currency column.** A `_amount` column without an adjacent `_currency` is a bug.
- **Java side**: map to `BigInteger` or wrap in a `Money` value object (`Money(long amount, Currency currency)`). Never `double`.

### Why not NUMERIC

`NUMERIC` is precise but allocation-heavy and slow in Java. `BIGINT` ranges up to ~9.2 quintillion satang — about 9.2 × 10¹⁶ THB, which is enough.

### Multi-currency display

Reporting in a base currency? Store the captured FX rate on the row at the time of the transaction:

```sql
-- orders
fx_rate_to_base    NUMERIC(18,8),
base_currency      CHAR(3),
base_total_amount  BIGINT
```

Live rates live in `exchange_rates` (catalog DB). For historical accuracy, **never recompute** orders against the current rate — use the captured one.

---

## 3. Timestamps

```sql
created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
```

- **Always `TIMESTAMPTZ`** — stores UTC, presents in session timezone.
- **Never `TIMESTAMP`** (no timezone) — guaranteed bug when a user is in a different timezone.
- **`NOW()` for defaults**, not `CURRENT_TIMESTAMP` (same value, but `NOW()` is the convention here).
- **Java**: map to `OffsetDateTime` or `Instant`. Never `Date` or `LocalDateTime` for stored data.

`updated_at` is bumped automatically by the `set_updated_at()` trigger. Don't set it from application code.

---

## 4. Audit Fields

Every business table has:

```sql
created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
created_by  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
updated_by  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
version     BIGINT       NOT NULL DEFAULT 0,
deleted_at  TIMESTAMPTZ                       -- soft delete (where applicable)
```

- **`version`** is for JPA `@Version` (optimistic locking). Don't touch from SQL.
- **`created_by` / `updated_by`** are only set within `catalog_db` (where `users` live). In `order_db` and `payment_db`, use `user_public_id` instead.
- **`deleted_at`** for soft delete. We use it on entities where history matters (`products`, `users`). We do **not** soft-delete things like `orders` or `payments` — those are immutable history.

---

## 5. Audit Log

Each service owns its own `audit_log` table. Triggers populate it automatically on every `INSERT` / `UPDATE` / `DELETE`.

```sql
CREATE TABLE audit_log (
    id            BIGSERIAL,
    entity_table  TEXT         NOT NULL,
    entity_id     BIGINT       NOT NULL,
    action        TEXT         NOT NULL CHECK (action IN ('INSERT','UPDATE','DELETE')),
    actor_id      BIGINT,                       -- nullable — populated from SET LOCAL
    old_data      JSONB,
    new_data      JSONB,
    changed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, changed_at)
) PARTITION BY RANGE (changed_at);
```

### How it works

1. Each business table calls `SELECT attach_standard_triggers('table_name')` at the end of its migration.
2. `attach_standard_triggers` wires up `trg_set_updated_at` (BEFORE UPDATE) and `trg_audit` (AFTER INSERT/UPDATE/DELETE).
3. The audit trigger reads `current_setting('audit.actor_id', true)` to record who made the change.

### Setting the actor (planned)

When Spring Security is wired up, each request will execute the following at the start of its DB transaction:

```sql
SET LOCAL audit.actor_id = '<user.id from JWT>';
```

This is a Postgres session variable, scoped to the transaction. We'll do this via an AOP interceptor or a `TransactionalAdvice`.

### Partitioning

The table is partitioned by month. The initial migration pre-creates the current month + the next two. **You need a recurring job** that pre-creates the next month's partition before it's needed (planned: cron in payment-service or a separate scheduler).

To archive old data:

```sql
ALTER TABLE audit_log DETACH PARTITION audit_log_202401;
-- ... export to S3 or COPY to a cold storage table ...
DROP TABLE audit_log_202401;
```

---

## 6. Snapshot Pattern

When data crosses a service boundary, **snapshot** the fields you'll need.

Example — `order_items` references `product_variants` (in catalog) via UUID, but stores everything needed to display the order forever:

```sql
CREATE TABLE order_items (
    variant_public_id     UUID NOT NULL,    -- soft ref
    product_public_id     UUID NOT NULL,
    product_name          TEXT NOT NULL,    -- snapshot
    variant_label         TEXT,             -- snapshot
    sku                   TEXT NOT NULL,    -- snapshot
    image_url             TEXT,             -- snapshot
    snapshot              JSONB NOT NULL,   -- everything else, for forensics
    unit_price_amount     BIGINT NOT NULL,  -- snapshot
    unit_price_currency   CHAR(3) NOT NULL,
    ...
);
```

### Rules

1. **Snapshot at write time** — when you create the row.
2. **Never `UPDATE` a snapshot** to "refresh" it — that defeats the purpose.
3. **Use `JSONB snapshot`** for the long tail of fields you might want later (audit, support, refund disputes). It costs nothing if unused.
4. **Schema flexibility**: the source service can rename a field and the snapshot is unaffected.

---

## 7. Naming Conventions

| Thing | Rule | Example |
|---|---|---|
| Tables | `snake_case`, **plural** | `products`, `order_items` |
| Columns | `snake_case`, **singular** | `product_id`, `email_verified` |
| Primary key | always `id` | `id BIGSERIAL PRIMARY KEY` |
| Foreign key column | `<table>_id` (singular table) | `vendor_id`, `category_id` |
| Cross-service ref | `<entity>_public_id` (UUID) | `user_public_id`, `order_public_id` |
| Boolean | starts with `is_` / `has_` / `was_` | `is_default`, `email_verified` |
| Timestamp | ends with `_at` | `created_at`, `delivered_at` |
| Money pair | `<x>_amount` + `<x>_currency` | `price_amount`, `price_currency` |
| Junction table | both table names, alphabetical | `variant_option_values` |
| Index | `idx_<table>_<columns>` | `idx_orders_user_placed` |
| Unique index | `uq_<table>_<columns>` | `uq_products_vendor_slug` |
| Constraint | `<table>_<purpose>_chk` | `orders_total_chk` |
| FK constraint | `fk_<table>_<other>` | `fk_addresses_vendor` |

---

## 8. Migrations (Flyway)

### Layout

```
src/main/resources/db/
├── migration/                    ← always applied (prod + dev + local)
│   ├── V1__init_extensions.sql
│   ├── V2__init_helpers.sql
│   └── V<n>__<description>.sql
└── seed/                          ← local + dev only (via profile override)
    └── R__seed_dev_data.sql
```

`db/seed/` is loaded only when `spring.flyway.locations` includes `classpath:db/seed`. See `application-local.yml`.

### Rules

1. **Once applied, never edit.** If you need to change something, write `V<n+1>__alter_xxx.sql`.
2. **One concern per migration.** Easier to review, easier to revert mentally.
3. **Always additive in prod.** Adding columns is safe. Dropping or renaming columns is a multi-step dance — see "Zero-Downtime Migrations" below.
4. **Migrations must be idempotent on partial failure.** Use `IF NOT EXISTS` where possible.
5. **Migrations are DDL.** Don't `INSERT` data into business tables from migrations (except `db/seed/`).

### Naming

- `V<number>__<snake_case_description>.sql` — applied once, ordered by number
- `R__<description>.sql` — applied on every Flyway run when its checksum changes (repeatable)

Order within a service:

| # | What |
|---|---|
| V1 | `init_extensions.sql` — Postgres extensions (`pgcrypto`, `uuid-ossp`, `pg_trgm`, `citext`) |
| V2 | `init_helpers.sql` — `uuid_generate_v7()`, `set_updated_at()`, `audit_trigger_fn()`, `attach_standard_triggers()` |
| V3+ | Business tables, in dependency order |
| Vn (last) | `create_audit_log.sql` — the actual `audit_log` table (referenced by the trigger function from V2) |

### Zero-Downtime Migrations

To drop a column safely in prod with a running app:

1. **Release 1**: Stop writing to the column (code change).
2. **Release 2**: Stop reading from the column (code change).
3. **Release 3**: Migration that drops the column.

Never do all three in one release. To rename a column:

1. **Release 1**: Add the new column. Backfill from old.
2. **Release 2**: Write to both columns. Read from new.
3. **Release 3**: Stop writing to old. Eventually drop old.

---

## 9. Postgres Extensions We Use

Every service installs these in `V1__init_extensions.sql`:

| Extension | Why |
|---|---|
| `pgcrypto` | `gen_random_bytes()` for UUID v7 implementation |
| `uuid-ossp` | Fallback UUID v4 generation |
| `pg_trgm` | Trigram GIN indexes for fuzzy search (`products.name`, `vendors.name`) |
| `citext` | Case-insensitive text — used for `email`, `slug` |
| `btree_gin` | Composite GIN indexes (catalog only) |

---

## 10. Indexing Rules of Thumb

- **Every foreign key column gets an index.** Postgres doesn't add one automatically.
- **Partial indexes** for soft-deleted rows: `WHERE deleted_at IS NULL`. Smaller index, faster queries on live data.
- **Composite indexes** for hot query paths. Order columns by selectivity (most selective first) **except** when used for sorting (then match the `ORDER BY` order).
- **`GIN` for full-text search** + `pg_trgm` for fuzzy match.
- **Don't over-index.** Every index slows writes and consumes RAM. Drop unused indexes (`pg_stat_user_indexes`).

---

## 11. Race-Condition Hot Spots

These are the places where concurrency will hurt you. Code accordingly.

| Spot | Risk | Mitigation |
|---|---|---|
| Inventory decrement at checkout | Oversell | `SELECT ... FOR UPDATE` on inventory row, or atomic `UPDATE inventory SET on_hand_qty = on_hand_qty - ? WHERE on_hand_qty >= ?` |
| Coupon usage limit | Two users redeem the last allowed use | Same atomic update pattern |
| Vendor payout amount calc | Double-paying a sub_order | Mark `sub_orders.payout_public_id` in same TX as creating the `vendor_payouts` row |
| Webhook idempotency | Gateway retries → double-charge | `payment_idempotency_keys` table + `ON CONFLICT DO NOTHING` |
| Cart merge (anon → logged in) | Double items | Merge logic in a single TX with `SELECT FOR UPDATE` on both carts |

---

## 12. Things Deliberately NOT in the Schema (yet)

- **Sharding keys** — single Postgres per service for now. Add when one service crosses ~100GB.
- **Full-text search vectors** (`tsvector`) — we use trigram for now; will add `tsvector` columns when we move search to a dedicated index.
- **Geo columns** (`PostGIS`) — no geo features yet.
- **Vector embeddings** (`pgvector`) — no recommendation engine yet.
- **CDC slots / replication** — will add when we start streaming to a data warehouse.

When adding any of the above, update this doc.
