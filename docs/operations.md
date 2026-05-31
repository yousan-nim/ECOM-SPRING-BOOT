# Operations

Day-to-day Docker, Makefile, and troubleshooting reference. Bookmark this.

---

## 1. Three Compose Profiles

We have **one base** `docker-compose.yml` and **three overrides**. Pick one:

| Profile | Override file | What runs | When to use |
|---|---|---|---|
| **local** | `docker-compose.local.yml` | 3 DBs + pgAdmin only (apps from IDE) | Day-to-day dev with hot reload |
| **local-full** | `docker-compose.local.yml` + `--profile full` | Everything in containers | Smoke test the full stack locally |
| **dev** | `docker-compose.dev.yml` | Full stack, dev profile, swagger on | Shared dev environment |
| **prod** | `docker-compose.prod.yml` | Full stack, prod profile, swagger off, resource limits | Production-like setup |

All of these layer over `docker-compose.yml` — they don't replace it.

---

## 2. Makefile Cheat Sheet

```bash
make help                 # list all targets

# ── Stack lifecycle ──────────────────────────────
make up-local             # 3 Postgres + pgAdmin only
make up-local-full        # + 3 services + gateway (containerized)
make up-dev               # dev profile, all containers
make up-prod              # prod profile (needs prebuilt/pulled images)
make down-local           # stop local stack
make down-dev             # stop dev stack
make down-prod            # stop prod stack
make db-reset             # DESTRUCTIVE — wipes all DB volumes

# ── Migrations ───────────────────────────────────
make migrate-all          # apply migrations to all 3 DBs
make migrate-catalog      # only catalog DB
make migrate-order        # only order DB
make migrate-payment      # only payment DB

# ── Database shells ──────────────────────────────
make tables               # list tables in all 3 DBs
make psql-catalog         # psql into catalog DB
make psql-order           # psql into order DB
make psql-payment         # psql into payment DB

# ── Build ────────────────────────────────────────
make build                # all 3 services
make build-catalog        # only catalog
make build-order          # only order
make build-payment        # only payment
make test                 # run all tests
make wrapper              # generate Maven wrapper (./mvnw)
```

---

## 3. Service Ports

When all services are running, here's the full port map:

| Port | Host | Container | What |
|---|---|---|---|
| 8080 | localhost:8080 | `ecom-gateway` | nginx gateway (main entry point) |
| 8081 | localhost:8081 | `ecom-catalog` | catalog-service direct |
| 8082 | localhost:8082 | `ecom-order` | order-service direct |
| 8083 | localhost:8083 | `ecom-payment` | payment-service direct |
| 5050 | localhost:5050 | `ecom-pgadmin` | pgAdmin web UI |
| 5432 | localhost:5432 | `ecom-postgres-catalog` | catalog Postgres |
| 5433 | localhost:5433 | `ecom-postgres-order` | order Postgres |
| 5434 | localhost:5434 | `ecom-postgres-payment` | payment Postgres |

In `prod` profile, Postgres ports are NOT exposed (internal network only).

---

## 4. URLs Worth Bookmarking

**Via gateway** (the production-realistic way — unified `/api/v1/*` namespace,
no service name in the URL):

- `http://localhost:8080/` — gateway info
- `http://localhost:8080/api/v1/auth/register` — register (→ user-service)
- `http://localhost:8080/api/v1/auth/login` — login (→ user-service)
- `http://localhost:8080/api/v1/users/me` — current user (→ user-service)

> Catalog/order/payment resources route the same way once their controllers
> exist — uncomment the matching `location` block in `infra/nginx/nginx.conf`.
> Health/liveness (`/api/v1/ping`, `/actuator/*`) is NOT gateway-routed; probe
> each instance directly.

**Direct** (for OpenAPI docs and debugging):

- `http://localhost:8084/swagger-ui.html` — user API
- `http://localhost:8081/swagger-ui.html` — catalog API
- `http://localhost:8082/swagger-ui.html` — order API
- `http://localhost:8083/swagger-ui.html` — payment API
- `http://localhost:8084/v3/api-docs` — user OpenAPI JSON
- `http://localhost:8084/actuator/health` — user health

**Infrastructure**:

- `http://localhost:5050` — pgAdmin (admin@ecom.dev / admin)

---

## 5. pgAdmin

On first login (admin@ecom.dev / admin), you'll see three pre-registered servers:

- **Catalog DB** — `postgres-catalog:5432/catalog_db` (login: `catalog` / `catalog`)
- **Order DB** — `postgres-order:5432/order_db` (login: `orderuser` / `orderpass`)
- **Payment DB** — `postgres-payment:5432/payment_db` (login: `paymentuser` / `paymentpass`)

If pgAdmin asks for the password when you expand a server, paste the matching one from above. (We don't store passwords in `servers.json` — that would be a secret in source control.)

---

## 6. Running Services Locally (without Docker for apps)

Common pattern: DBs in Docker, apps in IDE.

```bash
make up-local                      # DBs only
make migrate-all                   # apply schema

# In your IDE: run CatalogApplication / OrderApplication / PaymentApplication.
# Each picks up its application-local.yml automatically and connects to
# the right host port (5432 / 5433 / 5434).
```

Or from terminal:

```bash
mvn -pl services/catalog-service spring-boot:run
mvn -pl services/order-service   spring-boot:run
mvn -pl services/payment-service spring-boot:run
```

(In separate terminals.)

When apps run on the host but try to reach each other via the docker network name (e.g., `catalog-service:8081`), they'll fail. That's why `application-local.yml` overrides to `http://localhost:8081`.

---

## 7. Common Operations

### Reset everything

```bash
make db-reset              # destroys all 3 DB volumes
make migrate-all           # reapply schema (and seed for catalog)
```

### Reset just one DB

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml --env-file .env.local \
    rm -sfv postgres-order
docker volume rm ecom_postgres-order-data
make up-local
make migrate-order
```

### Inspect a partition

```bash
make psql-catalog
ecom=# \d+ audit_log              # see partitions
ecom=# SELECT count(*) FROM audit_log_202605;
```

### Tail logs

```bash
docker logs -f ecom-catalog
docker logs -f ecom-order
docker logs -f ecom-payment
docker logs -f ecom-gateway

# Or via compose:
make logs-local
```

### Rebuild one service image

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml --env-file .env.local \
    build catalog-service
```

### Run Flyway info without applying

```bash
docker run --rm --network ecom_ecom-net \
    -v "$(pwd)/services/catalog-service/src/main/resources/db/migration:/flyway/sql/migration" \
    flyway/flyway:10 \
    -url=jdbc:postgresql://postgres-catalog:5432/catalog_db \
    -user=catalog -password=catalog \
    -locations=filesystem:/flyway/sql/migration \
    info
```

---

## 8. Troubleshooting

### Containers won't start

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml ps
docker logs <container_name>
```

Common causes:

- **Port already in use**: `lsof -i :5432` to find the offender. Often a host-installed Postgres. Stop it or change ports in `docker-compose.local.yml`.
- **Volume corrupted**: `make db-reset` (destroys data).
- **Docker daemon not running**: `open -a Docker`.

### Migrations fail

- `relation "audit_log" does not exist` — you wrote a migration that depends on the audit_log being present. In each service, audit_log must be the last migration. Either reorder or split your change.
- `column ... is of type ... but expression is of type ...` — a `CHECK` constraint mismatch (e.g., money columns must be `BIGINT`). Fix the column type.
- `cannot drop ... because other objects depend on it` — you tried to drop a referenced object. Drop dependencies first or use `CASCADE` (carefully).

### "Flyway validation failed: Detected applied migration not resolved locally"

Someone deleted a migration file. Either restore it from git history or accept the local DB is unusable (`make db-reset`). **Never edit applied migrations** — that's the rule that prevents this.

### Service starts but JPA validation fails

Hibernate compared the entity to the schema and found a mismatch. Examples:

- Entity has a field; column doesn't exist (add migration to add it).
- Column has `NOT NULL`; entity doesn't enforce it (add `@Column(nullable = false)`).
- Column is `TIMESTAMPTZ`; entity uses `LocalDateTime` (change to `OffsetDateTime`).

The error message is verbose; read it carefully.

### Cross-service REST call fails

From inside a container, services see each other by **service name** (`catalog-service`, etc.), not `localhost`. Check that:

1. Your `application-local.yml` uses `http://localhost:8081` for IDE runs.
2. Your `application.yml` uses `http://catalog-service:8081` for container runs.
3. All services are on the same docker network (`ecom_ecom-net`).

### pgAdmin restarts in a loop

Usually an email-validation issue. We allow `.local` and `.dev` TLDs via `PGADMIN_CONFIG_ALLOW_SPECIAL_EMAIL_DOMAINS`. If you change `PGADMIN_EMAIL` to a custom TLD, add it to the list in `docker-compose.yml`.

### Audit log triggers don't fire

Check that the table has the trigger:

```sql
SELECT tgname, tgrelid::regclass
FROM pg_trigger
WHERE tgname LIKE 'trg_audit%';
```

If empty, the migration forgot to call `SELECT attach_standard_triggers('<table>')` at the end.

---

## 9. Building Production Images

Local build:

```bash
make build-catalog                              # produces catalog-service.jar
docker build -f services/catalog-service/Dockerfile -t ecom/catalog-service:0.1.0 .
```

Note the build context is the **repo root** (`.`), not the service directory — the Dockerfile copies the parent pom + all module poms for Maven dependency resolution.

Tag and push:

```bash
docker tag ecom/catalog-service:0.1.0 ghcr.io/<your-org>/catalog-service:0.1.0
docker push ghcr.io/<your-org>/catalog-service:0.1.0
```

Update `.env.prod` with the registry URL and tag, then `make up-prod`.

---

## 10. Backup & Restore (development)

Dump a single DB:

```bash
docker exec ecom-postgres-catalog pg_dump -U catalog -d catalog_db -Fc -f /tmp/catalog.dump
docker cp ecom-postgres-catalog:/tmp/catalog.dump ./catalog.dump
```

Restore:

```bash
docker cp ./catalog.dump ecom-postgres-catalog:/tmp/catalog.dump
docker exec ecom-postgres-catalog pg_restore -U catalog -d catalog_db --clean /tmp/catalog.dump
```

For prod, this approach won't scale — use the cloud provider's snapshot/PITR features (RDS, Cloud SQL, etc.).

---

## 11. Resource Tuning

Defaults are conservative for local dev. For prod:

| Knob | Default | Prod hint |
|---|---|---|
| `JAVA_OPTS` `MaxRAMPercentage` | 75% | Keep at 75% so container limits work |
| HikariCP `maximum-pool-size` | 20 | Increase to (CPU × 2 + 1) per replica |
| Postgres `shared_buffers` | 128MB | 25% of available RAM (set via image env or my.cnf) |
| Postgres `max_connections` | 100 | Match `(replicas × pool_size) + headroom` |
| Hibernate `jdbc.batch_size` | 30 | Already set, increase for bulk-write services |

Always test under load (k6 / Gatling) before tuning blindly.

---

## 12. Security Checklist (before any prod deploy)

- [ ] No default passwords in `.env.prod` (`__set_me__` placeholders are still there).
- [ ] Postgres ports are not exposed to the host.
- [ ] Swagger is disabled (`SWAGGER_ENABLED=false`).
- [ ] Spring Security is wired (planned).
- [ ] Secrets come from a vault, not env files committed to git.
- [ ] TLS between gateway and clients (terminate at LB or nginx).
- [ ] Database backups are scheduled and tested.
- [ ] Audit log retention policy is defined.
- [ ] Webhook endpoints verify signatures.
- [ ] Card data is never logged (PCI scope).
