# Development Guide

How to do common tasks in this codebase. Keep this updated as patterns evolve.

---

## 1. Prerequisites

- **Java 21** (verify with `java -version`)
- **Maven 3.9+** (or use the wrapper once you've generated it with `make wrapper`)
- **Docker Desktop** (or compatible — Colima, Rancher Desktop)
- **An IDE** that understands Maven multi-module (IntelliJ IDEA strongly recommended)

Optional but useful:

- **psql** CLI on host (`brew install libpq && brew link --force libpq`)
- **httpie** or **curl** for API smoke tests
- **jq** for pretty-printing JSON responses

---

## 2. First-Time Setup

```bash
git clone <repo>
cd ECOM-SPRING-BOOT

# Bring up DBs only
make up-local

# Apply migrations to all 3 DBs
make migrate-all

# (Optional) generate Maven wrapper for self-contained builds
make wrapper

# Verify
make tables
```

Then open IntelliJ, **Import as Maven project**, point to the root `pom.xml`. The IDE will discover the 3 service modules automatically.

To run a service from the IDE:

1. Open `services/<service>/src/main/java/com/ecom/<domain>/<Service>Application.java`
2. Right-click → **Run**
3. Make sure `SPRING_PROFILES_ACTIVE=local` is set in the run config (IDE often auto-detects from `application.yml`)

Or from the command line:

```bash
mvn -pl services/catalog-service spring-boot:run
```

---

## 3. Adding a Migration

> Always: one concern per migration. Don't bundle unrelated changes.

### To an existing service

```bash
cd services/<service>/src/main/resources/db/migration
# Look at the current max version number
ls -1 V*.sql | sort -V | tail -1
# Create the next one
vim V<n+1>__add_<thing>.sql
```

Template for a new table:

```sql
-- V12__add_wishlists.sql

CREATE TABLE wishlists (
    id              BIGSERIAL    PRIMARY KEY,
    public_id       UUID         NOT NULL DEFAULT uuid_generate_v7() UNIQUE,
    user_public_id  UUID         NOT NULL,            -- soft ref to catalog.users
    name            TEXT         NOT NULL,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_wishlists_user ON wishlists (user_public_id) WHERE deleted_at IS NULL;

SELECT attach_standard_triggers('wishlists');
```

Apply locally:

```bash
make migrate-order        # or migrate-catalog / migrate-payment as appropriate
```

### Rules (recap from [database-design.md](database-design.md))

- **Once applied, never edit.** Write a new migration to fix mistakes.
- **No data inserts** in `db/migration/`. Use `db/seed/R__*.sql` for local-only data.
- **Always end with `attach_standard_triggers('<table>')`** for business tables — this wires up `updated_at` and audit.
- **Always include `version BIGINT`** on tables you'll map with JPA — needed for `@Version`.

---

## 4. Adding a REST Endpoint

Each service follows this package structure:

```
com.ecom.<domain>/
├── <Domain>Application.java           ← @SpringBootApplication
├── config/                             ← OpenAPI, security, beans
├── web/                                ← @RestController classes
│   ├── HealthController.java
│   └── ...
├── service/                            ← business logic (later)
├── repository/                         ← Spring Data repos (later)
└── domain/                             ← JPA entities + value objects (later)
```

For a new endpoint:

```java
// services/catalog-service/src/main/java/com/ecom/catalog/web/ProductController.java
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product catalog browsing.")
public class ProductController {

    @GetMapping("/{publicId}")
    @Operation(summary = "Get product by public ID")
    public ProductResponse getById(@PathVariable UUID publicId) {
        // ...
    }
}
```

### Conventions

- **Path prefix is `/api/v1/`** — versioned from day 1.
- **Use plural nouns** for resource paths: `/products`, not `/product`.
- **Path variables use `public_id` (UUID)**, never the internal `BIGSERIAL`.
- **DTOs**, not entities, in responses. Annotate with `@Schema` for OpenAPI.
- **Validation** at the controller: `@Valid` on request bodies, custom validators where needed.
- **Errors**: throw a domain exception → global handler → RFC 7807 Problem Details JSON.

---

## 5. JPA Entity Mapping

(Once we start adding entities — none in the repo yet beyond the migration schemas.)

```java
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                              // BIGSERIAL — internal use

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;                        // UUID v7 — exposed externally

    @Column(nullable = false)
    private String name;

    @Column(name = "price_amount", nullable = false)
    private Long priceAmount;                     // satang

    @Column(name = "price_currency", nullable = false, length = 3)
    private String priceCurrency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;                         // optimistic locking
}
```

### Rules

- **`ddl-auto: validate`** in `application.yml` — Hibernate validates the schema, never generates it. Flyway is the source of truth.
- **`open-in-view: false`** — already set. Don't turn it back on. Lazy loading errors are a feature, not a bug.
- **No `@OneToMany(fetch = EAGER)`** — guaranteed N+1.
- **Use `@EntityGraph` or `JOIN FETCH`** when you genuinely need a related entity.
- **JPA `@Version` ↔ DB `version` column** — Hibernate increments it; the DB has a default of 0 for inserts.
- **Map enums as TEXT**: the DB has `CHECK` constraints. Use `@Enumerated(EnumType.STRING)` + matching enum.
- **Always `@Column(updatable = false)`** on `created_at` and `public_id`.

---

## 6. Service-to-Service Calls (REST)

When `order-service` needs product info from `catalog-service`:

```java
// Use a configured WebClient or RestClient bean
@Service
@RequiredArgsConstructor
public class CatalogClient {
    private final RestClient catalogClient;       // configured with base URL from application.yml

    public ProductDto getProduct(UUID publicId) {
        return catalogClient.get()
                .uri("/api/v1/products/{id}", publicId)
                .retrieve()
                .body(ProductDto.class);
    }
}
```

Base URLs come from `application.yml`:

```yaml
app:
  services:
    catalog: ${CATALOG_BASE_URL:http://catalog-service:8081}
```

### Rules

- **Never hardcode** another service's URL — always via config.
- **Use `RestClient`** (Spring 6.1+), not the deprecated `RestTemplate`.
- **Wrap in a `*Client` class** — controller / service should not see the HTTP details.
- **Don't `throw` inter-service HTTP errors** as-is to the caller — translate to a domain exception.
- **Plan for failure**: add timeouts (`Duration.ofSeconds(2)`), retries (when Resilience4j is wired), and a circuit breaker.
- **Idempotency**: any POST to another service that mutates state must carry an `Idempotency-Key` header (we'll formalize this when wiring payment integration).

---

## 7. Testing

### Unit tests

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository repo;
    @InjectMocks ProductService service;

    @Test
    void findsExistingProduct() { ... }
}
```

### Integration tests with Testcontainers

```java
@SpringBootTest
@Testcontainers
class ProductFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ProductService service;

    @Test
    void productCanBeCreatedAndFetched() { ... }
}
```

`@ServiceConnection` (Spring Boot 3.1+) wires the container's URL/credentials into Spring's datasource automatically — no manual `@DynamicPropertySource`.

### Rules

- **Unit tests** are pure JUnit + Mockito. No Spring context.
- **Integration tests** spin a real Postgres via Testcontainers — they validate Flyway migrations + JPA entity mappings end-to-end.
- **Don't mock the database.** A `@DataJpaTest` against H2 is a lie — use Testcontainers.
- **Slice tests** (`@WebMvcTest`, `@DataJpaTest`) for narrow scope; full `@SpringBootTest` for end-to-end.
- **Name pattern**: `*Test` for unit, `*IT` for integration. Maven Surefire picks up `*Test`; Failsafe (configure later) picks up `*IT`.

---

## 8. Adding a New Service

Use this checklist. Replace `<name>` with the service name (e.g., `notification`).

1. **Create the directory**:
   ```
   services/<name>-service/
   ├── pom.xml
   ├── Dockerfile
   └── src/main/{java,resources}
   ```
2. **Copy `pom.xml` from `order-service`**, change `<artifactId>` and `<finalName>` to `<name>-service`.
3. **Add the module to the root `pom.xml`** `<modules>` block.
4. **Create the Spring Boot main class** under `com.ecom.<name>.<Name>Application`.
5. **Copy `OpenApiConfig` + `HealthController`** from a sibling service, adjust package and title.
6. **Create `application.yml` + `application-local.yml`** with a unique port (next available — 8084+).
7. **Create `db/migration/V1__init_extensions.sql` and `V2__init_helpers.sql`** by copying from a sibling.
8. **Copy `Dockerfile`** from a sibling, adjust the `-pl services/<name>-service` flag and the exposed port.
9. **Add a new Postgres container** in `docker-compose.yml`:
   ```yaml
   postgres-<name>:
     image: postgres:16-alpine
     environment:
       POSTGRES_DB: <name>_db
       POSTGRES_USER: <name>user
       POSTGRES_PASSWORD: <name>pass
     ...
   ```
10. **Add the service to `docker-compose.yml`** (image, env, depends_on).
11. **Expose its port in `docker-compose.local.yml`** + add to the `--profile full` group.
12. **Add a `migrate-<name>` Makefile target**.
13. **Add the new server to `docker/pgadmin/servers.json`**.
14. **Add a gateway route** in `infra/nginx/nginx.conf`:
    ```nginx
    upstream <name>_upstream { server <name>-service:808X; }
    location /<name>/ {
        rewrite ^/<name>/(.*)$ /$1 break;
        proxy_pass http://<name>_upstream;
        ...
    }
    ```
15. **Update env templates** (`.env.local.example`, `.env.dev.example`, `.env.prod.example`) with new DB credentials.
16. **Update [architecture.md](architecture.md)** with the new service's responsibility.

Then:

```bash
make up-local
make migrate-<name>
mvn -pl services/<name>-service spring-boot:run
```

---

## 9. Commit & Branching Conventions

(Adopt formally when team grows. Suggested:)

- **Branches**: `feat/<topic>`, `fix/<topic>`, `chore/<topic>`, `docs/<topic>`.
- **Commits**: Conventional Commits — `feat(catalog): add product search endpoint`.
- **PRs**: one logical change per PR. Cross-service changes should still be one PR — that's the monorepo benefit.
- **Migrations and code in the same PR**. Never merge a code change that depends on an unmerged migration (or vice versa).

---

## 10. Common Pitfalls

| Symptom | Likely cause |
|---|---|
| `relation "audit_log" does not exist` during migration | The `audit_trigger_fn()` function references `audit_log`, but the audit_log migration must be the **last** migration in each service. Don't reorder. |
| `@Transactional` not working | Self-invocation (calling a `@Transactional` method from another method in the same class). Refactor to two beans. |
| N+1 query in logs | Missing `@EntityGraph` / `JOIN FETCH`. See [database-design.md §10](database-design.md#10-indexing-rules-of-thumb). |
| Hibernate `LazyInitializationException` | Lazy attribute accessed outside a transaction. Fix the query, not by enabling `open-in-view`. |
| `WHERE deleted_at IS NULL` everywhere | Use a Hibernate `@SQLRestriction` (Hibernate 6) on the entity. |
| Postgres connection pool exhausted | HikariCP defaults to 20. Tune `spring.datasource.hikari.maximum-pool-size` and verify with `leak-detection-threshold`. |
| Cross-service FK temptation | Stop. See [architecture.md §3](architecture.md#3-the-database-per-service-rule). |
| Migration applied differently between local and prod | Did you edit a migration after it was applied somewhere? That's forbidden — see "Once applied, never edit". |

---

## 11. Where to Look for X

| You want to | Look here |
|---|---|
| Add a column to existing table | New migration in `services/<svc>/src/main/resources/db/migration/V<n+1>__alter_*.sql` |
| Add a Spring bean | `services/<svc>/src/main/java/com/ecom/<domain>/config/` |
| Add a config property | `services/<svc>/src/main/resources/application.yml` + a `@ConfigurationProperties` class |
| Adjust nginx routing | `infra/nginx/nginx.conf` |
| Add a Docker volume / network | `docker-compose.yml` |
| Add a Makefile shortcut | `Makefile` |
| Tweak default seed data | `services/catalog-service/src/main/resources/db/seed/R__seed_dev_data.sql` |
| Wire a new dependency | `services/<svc>/pom.xml` (or parent `pom.xml` if shared) |
