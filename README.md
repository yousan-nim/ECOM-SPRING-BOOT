# ECOM Platform

Production-grade e-commerce platform built as a **Maven multi-module monorepo** with **3 microservices**, each owning its own PostgreSQL database.

```
                        ┌──────────────────────┐
                        │  nginx gateway :8080 │
                        └──────────┬───────────┘
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
       ┌────────────┐       ┌────────────┐       ┌────────────┐
       │  catalog   │       │   order    │       │  payment   │
       │   :8081    │       │   :8082    │       │   :8083    │
       └─────┬──────┘       └─────┬──────┘       └─────┬──────┘
             │                    │                    │
             ▼                    ▼                    ▼
       ┌──────────┐         ┌──────────┐         ┌──────────┐
       │ catalog_ │         │  order_  │         │ payment_ │
       │    db    │         │    db    │         │    db    │
       └──────────┘         └──────────┘         └──────────┘
```

---

## Quick Start

```bash
# 1) Bring up the 3 databases + pgAdmin
make up-local

# 2) Apply migrations to all 3 databases
make migrate-all

# 3) Verify
make tables                      # lists tables in all 3 DBs
open http://localhost:5050       # pgAdmin (3 servers auto-registered)

# 4) Run a service from your IDE (or:)
make up-local-full               # build + run all 4 services + gateway
```

Then:

| URL | What |
|---|---|
| http://localhost:8080/ | Gateway info |
| http://localhost:8080/api/v1/auth/register | Register via gateway (user-service) |
| http://localhost:8080/api/v1/users/me | Current user via gateway (user-service) |
| http://localhost:8084/swagger-ui.html | User OpenAPI (direct) |
| http://localhost:8081/swagger-ui.html | Catalog OpenAPI (direct) |
| http://localhost:8082/swagger-ui.html | Order OpenAPI (direct) |
| http://localhost:8083/swagger-ui.html | Payment OpenAPI (direct) |
| http://localhost:5050 | pgAdmin (admin@ecom.dev / admin) |

> Health/liveness (`/api/v1/ping`, `/actuator/*`) is **not** routed through the gateway — hit each service directly (e.g. `http://localhost:8084/api/v1/ping`).

---

## Repository Layout

```
ECOM-SPRING-BOOT/
├── pom.xml                        ← parent POM (multi-module)
├── docker-compose.yml             ← base: 3 DBs + 3 services + gateway + pgAdmin
├── docker-compose.{local,dev,prod}.yml
├── Makefile                       ← every common task is a target
├── docs/                          ← READ THESE
│   ├── architecture.md            ← why the split, cross-service rules
│   ├── database-design.md         ← schema patterns, audit, UUIDs, money
│   ├── development.md             ← how to add migrations, endpoints, services
│   ├── operations.md              ← docker, troubleshooting, day-to-day ops
│   └── roadmap-expert.md          ← Spring Boot expert learning path
├── services/
│   ├── catalog-service/           ← users, vendors, products, variants, inventory
│   ├── order-service/             ← carts, orders, shipments, coupons, reviews
│   └── payment-service/           ← payments, refunds, payouts, webhooks
├── infra/
│   └── nginx/nginx.conf           ← API gateway routing
└── docker/
    └── pgadmin/servers.json       ← pre-registers all 3 DBs in pgAdmin
```

---

## Documentation

Read in this order:

1. **[Architecture](docs/architecture.md)** — service boundaries, the database-per-service rule, cross-service references, when to add a new service.
2. **[Database Design](docs/database-design.md)** — UUID v7 strategy, money handling, audit log, snapshot pattern, migration rules.
3. **[Development Guide](docs/development.md)** — adding a migration, adding an endpoint, JPA conventions, testing.
4. **[Operations](docs/operations.md)** — Docker workflow, Makefile targets, troubleshooting, common debugging.
5. **[Expert Roadmap](docs/roadmap-expert.md)** — long-term Spring Boot mastery path.

---

## Tech Stack

| Layer | Tool |
|---|---|
| Language / runtime | **Java 21** (LTS, virtual threads ready) |
| Framework | **Spring Boot 3.4** |
| Persistence | **Spring Data JPA + Hibernate 6** |
| Database | **PostgreSQL 16** (1 per service) |
| Migrations | **Flyway 10** |
| API docs | **springdoc-openapi 2.7** (Swagger UI) |
| Build | **Maven 3.9** (multi-module) |
| Containers | **Docker** + **docker compose** |
| Gateway | **nginx** (will swap to Spring Cloud Gateway when needed) |
| Tests | JUnit 5, Mockito, **Testcontainers** |

---

## Key Architectural Rules (don't break these)

1. **No cross-service foreign keys.** Refer to other services' entities by `public_id UUID` only.
2. **Snapshot data you depend on.** When the user places an order, copy the price + product name into `order_items` — never `JOIN` back across services.
3. **One database per service.** Never share a DB between services. If you need data, fetch via REST/event.
4. **Migrations are append-only.** Once a `V<n>` is applied to any environment, never edit it. Create `V<n+1>__alter_xxx.sql` instead.
5. **Money is `BIGINT` in the smallest unit** (satang for THB). Currency is a separate `CHAR(3)` column.
6. **Timestamps are `TIMESTAMPTZ`.** Always store UTC; display in user timezone.
7. **Public IDs are UUID v7.** Internal joins use `BIGSERIAL`; cross-service / API-exposed IDs use `public_id`.

Details and rationale in [docs/database-design.md](docs/database-design.md).

---

## License

Proprietary — internal project.
