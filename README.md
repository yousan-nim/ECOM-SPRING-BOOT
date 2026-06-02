<div align="center">

# ECOM Platform

**Production-grade e-commerce backend — Java 21 · Spring Boot 3.4 · PostgreSQL · Multi-module microservices.**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-10-cc0200.svg)](https://flywaydb.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9-c71a36.svg)](https://maven.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/license-Proprietary-lightgrey.svg)](#license)

[Quick Start](#quick-start) • [Architecture](#architecture) • [Endpoints](#endpoints) • [Configuration](#configuration) • [Docs](#documentation)

</div>

---

## Overview

ECOM Platform is a **Maven multi-module monorepo** that ships an e-commerce backend as **4 independent Spring Boot services**, each owning its own PostgreSQL database. The split follows strict database-per-service boundaries — no cross-service joins, no shared schemas — so each service can scale, deploy, and evolve independently.

**Why this design**
- **Isolation** — a slow query in `order-service` cannot starve `catalog-service`.
- **Clear ownership** — every table has exactly one writer service.
- **Migration safety** — Flyway per service, append-only `V<n>__*.sql`, no cross-service refactors.
- **Future-proof** — services already talk by `public_id (UUID v7)`; swapping nginx for Spring Cloud Gateway, or moving a service to its own repo, is mechanical.

---

## Features

- Auth & user management with JWT (user-service)
- Catalog with vendors, products, variants, inventory (catalog-service)
- Order lifecycle, carts, shipments, coupons, reviews (order-service)
- Payments, refunds, payouts, webhooks (payment-service)
- nginx API gateway with single public entry point
- OpenAPI 3 / Swagger UI on every service
- Flyway migrations per database (with seed data)
- pgAdmin with all 4 databases auto-registered
- Docker Compose stacks for `local`, `dev`, `prod`
- `Makefile` for every common task (`make help`)

---

## Architecture

```
                            ┌──────────────────────┐
                            │  nginx gateway :8080 │
                            └──────────┬───────────┘
            ┌──────────────┬───────────┴───────────┬──────────────┐
            ▼              ▼                       ▼              ▼
       ┌─────────┐   ┌─────────┐             ┌─────────┐   ┌─────────┐
       │  user   │   │ catalog │             │  order  │   │ payment │
       │  :8084  │   │  :8081  │             │  :8082  │   │  :8083  │
       └────┬────┘   └────┬────┘             └────┬────┘   └────┬────┘
            │             │                       │             │
            ▼             ▼                       ▼             ▼
       ┌────────┐    ┌──────────┐            ┌─────────┐   ┌──────────┐
       │ user_  │    │ catalog_ │            │ order_  │   │ payment_ │
       │   db   │    │    db    │            │    db   │   │    db    │
       └────────┘    └──────────┘            └─────────┘   └──────────┘
```

Cross-service communication is by **`public_id` (UUID v7)** only — never by FK. When `order-service` needs product data, it either calls catalog over HTTP and **snapshots** the result into `order_items`, or consumes a domain event.

Full rationale in [`docs/architecture.md`](docs/architecture.md).

---

## Tech Stack

| Layer | Tool |
|---|---|
| Language / runtime | **Java 21** (LTS, virtual threads ready) |
| Framework | **Spring Boot 3.4** |
| Persistence | **Spring Data JPA + Hibernate 6** |
| Database | **PostgreSQL 16** (1 per service) |
| Migrations | **Flyway 10** |
| Auth | **JJWT 0.12** (JWT signing) |
| API docs | **springdoc-openapi 2.7** (Swagger UI) |
| Build | **Maven 3.9** (multi-module) |
| Containers | **Docker** + **docker compose** |
| Gateway | **nginx** (swap to Spring Cloud Gateway when needed) |
| Tests | JUnit 5, Mockito, **Testcontainers** |

---

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java | **21** (LTS) | `java -version` |
| Maven | 3.9+ (or use the included project conventions) | `mvn -v` |
| Docker | 24+ with Compose v2 | `docker --version && docker compose version` |
| GNU Make | 3.8+ | `make --version` |

**Tip:** install Java 21 via [SDKMAN](https://sdkman.io/) — `sdk install java 21-tem`.

---

## Quick Start

```bash
# 1) Start the 4 Postgres databases + pgAdmin
make up-local

# 2) Apply Flyway migrations to all 4 databases
make migrate-all

# 3) Verify schema
make tables                      # lists tables in every DB
open http://localhost:5050       # pgAdmin (servers pre-registered)

# 4a) Run services from your IDE — recommended for development
#     (one Spring Boot run config per service)

# 4b) …or run everything in containers
make up-local-full               # build + start all 4 services + gateway
```

You should now be able to hit:

| URL | What |
|---|---|
| `http://localhost:8080/` | Gateway info page |
| `http://localhost:8080/api/v1/auth/register` | Register (gateway → user-service) |
| `http://localhost:8080/api/v1/users/me` | Current user (gateway → user-service) |
| `http://localhost:8084/swagger-ui.html` | User-service OpenAPI |
| `http://localhost:8081/swagger-ui.html` | Catalog-service OpenAPI |
| `http://localhost:8082/swagger-ui.html` | Order-service OpenAPI |
| `http://localhost:8083/swagger-ui.html` | Payment-service OpenAPI |
| `http://localhost:5050` | pgAdmin (`admin@ecom.dev` / `admin`) |

> Health / liveness endpoints (`/api/v1/ping`, `/actuator/*`) are **not** routed through the gateway — hit each service directly (e.g. `http://localhost:8084/api/v1/ping`).

---

## Repository Layout

```
ECOM-SPRING-BOOT/
├── pom.xml                           ← parent POM (multi-module)
├── Makefile                          ← every common task is a target
├── docker-compose.yml                ← base: DBs + services + gateway + pgAdmin
├── docker-compose.local.yml          ← local overrides (IDE-friendly)
├── docker-compose.dev.yml            ← dev environment
├── docker-compose.prod.yml           ← prod environment
├── .env.{example,local,dev,prod}     ← env templates per environment
├── docs/                             ← READ THESE
│   ├── architecture.md               ← service split, cross-service rules
│   ├── database-design.md            ← UUID v7, money, audit, snapshots
│   ├── development.md                ← migrations, endpoints, JPA conventions
│   ├── operations.md                 ← docker, Makefile, troubleshooting
│   └── roadmap-expert.md             ← Spring Boot mastery path
├── services/
│   ├── user-service/                 ← auth, users, JWT      :8084
│   ├── catalog-service/              ← vendors, products     :8081
│   ├── order-service/                ← carts, orders         :8082
│   └── payment-service/              ← payments, refunds     :8083
├── infra/
│   └── nginx/nginx.conf              ← API gateway routing
└── docker/
    └── pgadmin/servers.json          ← pre-registers 4 DBs in pgAdmin
```

Inside each service:

```
services/<svc>/
├── Dockerfile
├── pom.xml
└── src/main/
    ├── java/com/ecom/<svc>/
    │   ├── config/        ← Spring config, beans
    │   ├── domain/        ← entities, value objects
    │   ├── repository/    ← Spring Data interfaces
    │   ├── service/       ← business logic
    │   ├── security/      ← JWT, filters (where applicable)
    │   └── web/           ← controllers + error handlers
    └── resources/
        ├── application.yml
        ├── application-local.yml
        └── db/migration/  ← Flyway V<n>__*.sql
```

---

## Endpoints

All public traffic enters through the gateway at `http://localhost:8080`. Below is a quick smoke test you can copy-paste.

### Register & log in
```bash
# 1) Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "P@ssw0rd!",
    "name": "Alice"
  }'

# 2) Log in → get JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"P@ssw0rd!"}' \
  | jq -r '.data.accessToken')

# 3) Authenticated request
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN"
```

### Per-service Swagger UI

| Service | Swagger | Direct ping |
|---|---|---|
| user | http://localhost:8084/swagger-ui.html | `GET :8084/api/v1/ping` |
| catalog | http://localhost:8081/swagger-ui.html | `GET :8081/api/v1/ping` |
| order | http://localhost:8082/swagger-ui.html | `GET :8082/api/v1/ping` |
| payment | http://localhost:8083/swagger-ui.html | `GET :8083/api/v1/ping` |

---

## Configuration

Per-environment env files: `.env.local`, `.env.dev`, `.env.prod`. The first `make up-*` will auto-copy from the matching `.env.*.example`.

| Variable | Default (local) | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | Spring profile per service |
| `SERVER_PORT` | per service (8081–8084) | HTTP port |
| `DB_URL` | `jdbc:postgresql://postgres-<svc>:5432/<svc>_db` | JDBC URL (container DNS) |
| `DB_USERNAME` / `DB_PASSWORD` | per service | Per-DB credentials |
| `DB_POOL_MAX` / `DB_POOL_MIN` | `20` / `5` | HikariCP pool sizing |
| `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` | `admin@ecom.dev` / `admin` | pgAdmin login |
| `SWAGGER_ENABLED` | `true` | Toggle Swagger UI (set `false` in prod) |
| `JAVA_OPTS` | `-XX:+UseG1GC -XX:MaxRAMPercentage=75.0` | JVM flags |
| `JWT_SECRET` | — **set in `.env.prod`** | HMAC signing key for JWT |

> **Production rule:** `.env.prod` is **not** committed. Edit it from the example file and store secrets in a real secret manager (Vault, AWS Secrets Manager, etc.).

---

## Makefile Targets

Run `make help` to see everything. Most-used:

| Target | What it does |
|---|---|
| `make up-local` | Start 4 Postgres + pgAdmin (no apps) |
| `make up-local-full` | Build + start all services + gateway |
| `make down-local` | Stop local stack |
| `make logs-local` | Tail local logs |
| `make migrate-all` | Apply Flyway migrations to all 4 DBs |
| `make migrate-<svc>` | Apply migrations for one service |
| `make tables` | List tables in every DB |
| `make psql-<svc>` | Open psql shell in a service's DB |
| `make build` | Build all services (`mvn -DskipTests package`) |
| `make build-<svc>` | Build a single service |
| `make test` | Run all tests |
| `make db-reset` | **DESTRUCTIVE** — drop & recreate all DB volumes |
| `make up-dev` / `make up-prod` | Bring up dev / prod stacks |

---

## Key Architectural Rules

These rules keep the system honest. Breaking them creates the kind of distributed monolith microservices were supposed to prevent.

1. **No cross-service foreign keys.** Refer to other services' entities by `public_id UUID` only.
2. **Snapshot data you depend on.** When the user places an order, copy `price` + `product_name` into `order_items` — never `JOIN` back across services.
3. **One database per service.** Never share a DB. Need data? Fetch via REST or an event.
4. **Migrations are append-only.** Once a `V<n>` is applied to any environment, never edit it. Create `V<n+1>__alter_xxx.sql` instead.
5. **Money is `BIGINT`** in the smallest unit (satang for THB). Currency is a separate `CHAR(3)` column.
6. **Timestamps are `TIMESTAMPTZ`.** Always store UTC; display in user timezone.
7. **Public IDs are UUID v7.** Internal joins use `BIGSERIAL`; cross-service / API-exposed IDs use `public_id`.

Rationale + examples in [`docs/database-design.md`](docs/database-design.md).

---

## Testing

```bash
# Run all tests across all services
make test
# or:
mvn -B test

# Run a single service's tests
mvn -B -pl services/user-service -am test

# Single test class
mvn -B -pl services/user-service test -Dtest=AuthServiceTest
```

Integration tests use **Testcontainers** to spin up real PostgreSQL — Docker must be running.

---

## Deployment

### Build images

```bash
# Build all service jars
make build

# Then build & start the dev/prod stack
make up-dev      # builds images on the fly
make up-prod     # expects images to be prebuilt or available in registry
```

### Production checklist

- [ ] `.env.prod` filled in — **never commit**
- [ ] `JWT_SECRET` set to a long random value (256-bit+)
- [ ] `SWAGGER_ENABLED=false`
- [ ] DB passwords rotated from defaults
- [ ] TLS terminated at gateway (nginx or upstream LB)
- [ ] All Flyway migrations applied (`make migrate-all`)
- [ ] Backups configured on each Postgres volume
- [ ] Log shipping configured (stdout → aggregator)
- [ ] `/actuator/health` wired to liveness/readiness probes

---

## Documentation

Read in this order:

1. **[Architecture](docs/architecture.md)** — service boundaries, the database-per-service rule, cross-service references, when to add a new service.
2. **[Database Design](docs/database-design.md)** — UUID v7 strategy, money handling, audit log, snapshot pattern, migration rules.
3. **[Development Guide](docs/development.md)** — adding a migration, adding an endpoint, JPA conventions, testing.
4. **[Operations](docs/operations.md)** — Docker workflow, Makefile targets, troubleshooting, common debugging.
5. **[Expert Roadmap](docs/roadmap-expert.md)** — long-term Spring Boot mastery path.

---

## Troubleshooting

<details>
<summary><b>Containers start but a service can't connect to its DB</b></summary>

Inside containers, JDBC URLs must use the **service name** (e.g. `postgres-user`), not `localhost`. Check the service's `application.yml` and `.env.local`:
```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml ps
docker logs ecom-postgres-user --tail=50
```
</details>

<details>
<summary><b>Flyway: "Validate failed: Migration checksum mismatch"</b></summary>

You edited a migration that was already applied. **Don't edit applied migrations.** Either:
- create `V<n+1>__fix.sql` with the correction, or
- for local-only resets: `make db-reset` then `make migrate-all` (DESTROYS data).
</details>

<details>
<summary><b>Port already in use (8080–8084, 5432, 5050)</b></summary>

```bash
lsof -i :8080      # find the offender
# stop conflicting process or change the host port in docker-compose.local.yml
```
</details>

<details>
<summary><b>pgAdmin doesn't show servers</b></summary>

The pre-registered servers come from `docker/pgadmin/servers.json`. If you started pgAdmin before this file existed, recreate the container:
```bash
make down-local
make up-local
```
</details>

<details>
<summary><b>JWT calls return 401 even with a token</b></summary>

- Check the token isn't expired (decode at https://jwt.io)
- Make sure `JWT_SECRET` is **identical** across all services that validate it
- Confirm the header is `Authorization: Bearer <token>` (not `Token`, not `JWT`)
</details>

<details>
<summary><b>Maven build fails on Lombok-generated methods</b></summary>

Your IDE needs Lombok annotation processing enabled:
- **IntelliJ:** *Settings → Build → Compiler → Annotation Processors → Enable*
- **VS Code:** install the *Lombok Annotations Support* extension
</details>

---

## Contributing

This is currently an internal/personal project, but the conventions are:

1. **Branch:** `feat/<scope>-<short-desc>`, `fix/<scope>-<short-desc>`
2. **Commits:** follow [Conventional Commits](https://www.conventionalcommits.org/)
   ```
   feat(catalog): add product variant inventory check
   fix(order): prevent double-checkout race
   docs: clarify snapshot rule in architecture.md
   ```
3. **Before opening a PR:**
   - `make build` passes
   - `make test` passes
   - new migrations follow `V<n>__<snake_case>.sql`
   - touched docs are updated

---

## Roadmap

- [x] 4-service monorepo with shared parent POM
- [x] Per-service PostgreSQL + Flyway migrations
- [x] nginx gateway + Swagger per service
- [x] JWT authentication (user-service)
- [ ] Inter-service auth (service tokens / mTLS)
- [ ] Event bus (Kafka / Rabbit) for order ↔ payment ↔ catalog
- [ ] Outbox pattern for reliable event publishing
- [ ] Replace nginx with Spring Cloud Gateway when routing logic grows
- [ ] CI/CD pipeline (GitHub Actions: build → test → push images)
- [ ] Observability stack (Prometheus + Grafana + Tempo/Loki)

Full long-term plan in [`docs/roadmap-expert.md`](docs/roadmap-expert.md).

---

## License

Proprietary — internal project. All rights reserved.
