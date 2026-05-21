# Architecture

This document captures the **why** behind the current shape of the platform. Read this before adding a new service or breaking a current boundary.

---

## 1. Topology

```
                        ┌──────────────────────┐
                        │  nginx gateway :8080 │
                        │  /catalog/* /order/* │
                        │       /payment/*     │
                        └──────────┬───────────┘
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
       ┌────────────┐       ┌────────────┐       ┌────────────┐
       │  catalog   │ ───►  │   order    │ ───►  │  payment   │
       │   :8081    │       │   :8082    │       │   :8083    │
       └─────┬──────┘       └─────┬──────┘       └─────┬──────┘
             │                    │                    │
             ▼                    ▼                    ▼
       ┌──────────┐         ┌──────────┐         ┌──────────┐
       │ catalog_ │         │  order_  │         │ payment_ │
       │    db    │         │    db    │         │    db    │
       └──────────┘         └──────────┘         └──────────┘
```

- **One process per service.** Each runs in its own container, its own JVM, with its own resource limits and rollout cycle.
- **One database per service.** No cross-service `JOIN`s, no shared schemas. Period.
- **One nginx gateway** for now. We'll swap to Spring Cloud Gateway when we need request-level auth, retries, rate limiting per route, etc.

---

## 2. Why this split

### catalog-service

Owns the **read-heavy** side of the marketplace.

- `users`, `user_roles`, `refresh_tokens`
- `vendors`, `vendor_bank_accounts`, `commission_rates`
- `addresses` (polymorphic — users or vendors)
- `categories` (hierarchical with materialized path)
- `products`, `product_images`
- `product_options`, `option_values`, `product_variants`, `variant_option_values`
- `warehouses`, `inventory`, `inventory_movements`
- `exchange_rates`

Rationale: browsing and search dominate traffic. We want to scale reads independently and eventually plug in Elasticsearch / OpenSearch alongside without disturbing the transactional path.

### order-service

Owns the **transactional** path from cart to fulfillment.

- `carts`, `cart_items`
- `orders`, `sub_orders`, `order_items`
- `coupons`, `coupon_usages`
- `shipments`, `shipment_events`, `shipment_items`
- `reviews`, `review_images`, `review_votes`

Rationale: this is the highest-stakes write path. Strong transactional guarantees, careful state machine. Reviews live here because they must reference an `order_items` row (verified purchase guarantee).

### payment-service

Owns **money movement** and is the riskiest service operationally.

- `payments`, `refunds`
- `payment_idempotency_keys`, `payment_webhook_events`
- `vendor_payouts`

Rationale: PCI scope, gateway webhooks, retries, and idempotency must not leak into the rest of the platform. Vendor payouts live here because they are a settlement concern (sums of money to disburse), not a catalog concern.

---

## 3. The Database-per-Service Rule

This is the most important rule in this codebase. Violating it turns microservices into a distributed monolith.

### Allowed

✅ `order.orders` references `catalog.users` by `user_public_id UUID` — **no foreign key**, no JOIN.
✅ `order.order_items` carries `snapshot JSONB` + denormalized `product_name`, `sku`, `image_url` so that historical orders survive product changes.
✅ `payment.payments` references `order.orders` by `order_public_id UUID` + `order_number` snapshot.
✅ Service A calls Service B's REST API to look up live data when needed.

### Forbidden

❌ Cross-database `JOIN`.
❌ Cross-service `FOREIGN KEY`.
❌ Two services reading/writing the same table.
❌ A service connecting to another service's database directly.

If you feel the urge to do any of the above, stop and ask: should this be one service, or should I be passing data via API/event?

---

## 4. Cross-Service Reference Pattern

```
catalog.users.public_id (UUID v7)
        │
        ▼
order.orders.user_public_id   ← soft ref, no FK
                + customer_email     ← snapshot
                + customer_name      ← snapshot
                + billing_address    ← snapshot (JSONB)
```

Every cross-service reference is a pair: **`<entity>_public_id` (UUID)** + **whatever fields you need at query time as snapshots**.

If the consumer service genuinely needs live data, it calls the owner service:

```
order-service ──► GET http://catalog-service:8081/api/v1/users/{public_id}
                  (called from a service or controller, never from a SQL trigger)
```

UUID v7 is sortable by time, so even soft references can be range-queried efficiently (`WHERE user_public_id > ?` returns recent users).

---

## 5. When to Call vs Snapshot vs Event

| Pattern | Use when | Trade-off |
|---|---|---|
| **Snapshot at write time** | Data must survive source changes (orders ↔ products) | Adds storage; needs occasional refresh job |
| **Live REST lookup** | Data must be current and read is infrequent | Latency, failure mode of upstream service |
| **Event-driven sync** (Kafka, planned) | Many consumers need to react to a change | Eventual consistency window, infra cost |
| **CDC replication** (Debezium, planned) | Read model elsewhere (data warehouse) | Extra moving piece, schema coupling |

Default: **snapshot for write paths, REST for occasional reads**. Adopt events when we have a real consumer count > 2.

---

## 6. Why Monorepo

We keep all three services in **one repo, one Maven build**:

✅ **Atomic cross-service changes**: a PR can update an API contract on the producer and bump the consumer in lock-step.
✅ **Shared tooling stays in sync**: same Spring Boot version, same Java version, same Lombok, same Flyway — no version drift.
✅ **Onboarding is one clone**: a new developer runs `make up-local-full` and has the whole platform on their laptop.
✅ **Refactor across boundaries**: IDE rename / type-check works across services.

Trade-offs we accept (and how to mitigate):

⚠️ **Risk of accidentally re-coupling services** — mitigated by the no-cross-service-FK rule and (planned) ArchUnit tests.
⚠️ **Selective CI** needed when the repo grows — Maven's `-pl <module> -am` flag plus GitHub Actions path filters will give us this.
⚠️ **Larger clone** — fine until repo is multi-GB; sparse-checkout works if needed.

We will split into separate repos only when we have **clear team boundaries** that don't map well onto a monorepo. Until then, a monorepo is simpler.

---

## 7. Why nginx Gateway (for now)

nginx is enough today because we only need:

- Path-based routing (`/catalog/*`, `/order/*`, `/payment/*`)
- Health endpoint
- Structured access logs

When we need any of these, swap to Spring Cloud Gateway:

- JWT validation at the edge
- Per-route rate limiting
- Request retries with backoff
- Circuit breaker per upstream
- Dynamic routing config

The swap is a single docker-compose change — the upstreams stay the same.

---

## 8. Adding a New Service — Decision Checklist

Before creating a fourth service, work through this:

1. **Does it own a distinct aggregate root?** (Notification, Search, Recommendation, Catalog-Read, etc.)
2. **Will it be deployed/scaled independently?** If not, it's a module in an existing service.
3. **Does it have its own data?** If it'd share a DB with an existing service, it's a feature, not a service.
4. **Who's the owning team?** Conway's Law: services tend to mirror team structure. One owner per service.
5. **What's the contract?** Define the REST/event contract before writing implementation.

If yes to all of the above, create a new module under `services/`. Boilerplate to copy:

- `services/<new-service>/pom.xml` (inherit from parent)
- `services/<new-service>/Dockerfile` (copy the pattern from `order-service`)
- `services/<new-service>/src/main/java/com/ecom/<domain>/...`
- `services/<new-service>/src/main/resources/{application.yml, application-local.yml, db/migration/V1__init_extensions.sql, V2__init_helpers.sql}`
- New DB container + entries in `docker-compose.yml`, `docker-compose.local.yml`, etc.
- New `migrate-<service>` target in `Makefile`
- Add to `pom.xml` `<modules>`

See `docs/development.md` for the step-by-step.

---

## 9. Planned Evolution (not built yet)

These are deliberately deferred until we need them:

- **Spring Security + JWT** — actor identity, role-based access, `audit.actor_id` wired through transactions
- **Service-to-service auth** — mTLS or signed JWT between services
- **Kafka** — event-driven sync, outbox pattern, replace REST for inter-service calls
- **Resilience4j** — circuit breaker, retry, bulkhead for REST calls
- **Observability** — Micrometer + Prometheus + Grafana; OpenTelemetry traces across all 3 services
- **Saga** — distributed transaction across order → payment → inventory
- **Outbox pattern** — atomic DB write + event publish
- **Read replicas + CQRS** — when read load forces it
- **Kubernetes** — replace docker-compose for prod when scale demands

Order of adoption matches the project [roadmap](roadmap-expert.md).
