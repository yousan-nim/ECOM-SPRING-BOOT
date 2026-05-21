# Spring Boot Expert Roadmap

เส้นทางสู่ Expert Spring Boot Developer สำหรับ Production-Grade E-Commerce API

> Timeline ประมาณ **12 เดือน → Senior+**, **18-24 เดือน → Expert ตัวจริง**
> (ถ้าทำ project จริงควบคู่ไปด้วย)

---

## Skill Map

```
                    ┌─────────────────────────┐
                    │   Architecture & DDD    │   ← ขั้นสูง
                    └─────────────────────────┘
              ┌─────────────────────────────────────┐
              │ Distributed Systems & Microservices │
              └─────────────────────────────────────┘
        ┌─────────────────────────────────────────────────┐
        │ Performance, JVM, Observability, Security       │
        └─────────────────────────────────────────────────┘
   ┌──────────────────────────────────────────────────────────┐
   │ Spring Internals, JPA/Hibernate Deep, Testing, Reactive  │
   └──────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────┐
│ Java 17/21, Spring Core, Spring Boot Fundamentals               │  ← ต้องแน่นก่อน
└─────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Foundation (เดือน 1-2) — Java + Spring Core

### Modern Java (17/21)
- **Records** — แทน DTO class แบบเก่า
- **Sealed classes** — pattern matching แบบ exhaustive
- **Pattern matching** (`switch`, `instanceof`)
- **Text blocks** — multi-line string
- **Stream API ลึก** — `collect`, `groupingBy`, `partitioningBy`, `teeing`
- **CompletableFuture** — async chain
- **Optional** ใช้ถูก (อย่าใช้แทน null check ทุกที่)
- **Functional interface** — `Function`, `BiFunction`, `Predicate`, `Supplier`
- **Virtual Threads (Java 21)** — Project Loom

### Spring Core (ห้ามข้าม)
- **ApplicationContext lifecycle** — refresh, close
- **Bean lifecycle** — `@PostConstruct`, `InitializingBean`, `BeanPostProcessor`
- **Bean scopes** — singleton, prototype, request, session
- **`@Conditional*`** — `@ConditionalOnProperty`, `@ConditionalOnClass`
- **Proxy** — JDK dynamic proxy vs CGLIB, self-invocation problem
- **AOP** — `@Aspect`, pointcut, around/before/after
- **SpEL** (Spring Expression Language)
- **Spring Events** — sync vs async, `@TransactionalEventListener`
- **Auto-configuration** — เข้าใจว่า `@EnableAutoConfiguration` ทำอะไร

> อ่านโค้ด Spring เองได้ = expert ตัวจริง ลอง breakpoint เข้าไปดู bean creation

---

## Phase 2: Persistence Layer (เดือน 2-3) — JPA/Hibernate Expert

### JPA / Hibernate Deep Dive
- **Entity lifecycle** — transient, managed, detached, removed
- **First-level cache** (persistence context)
- **Second-level cache** — Ehcache, Hazelcast
- **Fetch strategies** — LAZY vs EAGER, `JOIN FETCH`, `@EntityGraph`
- **N+1 problem** — ตรวจจับและแก้ได้
- **Cascade types** — PERSIST, MERGE, REMOVE, ALL
- **Orphan removal** vs `CascadeType.REMOVE`
- **Optimistic vs Pessimistic locking** — `@Version`, `PESSIMISTIC_WRITE`
- **Flush mode** — AUTO, COMMIT, MANUAL
- **Dirty checking**
- **Bag vs List vs Set** ใน mapping
- **Inheritance strategies** — SINGLE_TABLE, JOINED, TABLE_PER_CLASS

### Query Patterns
- JPQL / HQL
- Criteria API + **Specification** (สำคัญสำหรับ filter)
- Native query เมื่อจำเป็น
- Projection — Interface-based, DTO projection
- Pagination — **keyset vs offset** (offset ช้าเมื่อ data ใหญ่)

### Transaction Mastery
- **Propagation** — REQUIRED, REQUIRES_NEW, NESTED, SUPPORTS, MANDATORY
- **Isolation** — READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE
- Why `@Transactional` ไม่ทำงาน — private method, self-invocation, checked exception
- `TransactionTemplate` เมื่อ annotation ไม่พอ

---

## Phase 3: Web Layer (เดือน 3-4)

### REST API Best Practices
- RESTful design — resource naming, HTTP verb
- Status codes ถูก — 200/201/204/400/401/403/404/409/422/429/500
- Pagination — `Pageable`, HATEOAS
- API versioning — URL path, header, content negotiation
- **Idempotency** — idempotency key สำหรับ POST
- Error response format — **RFC 7807 Problem Details**
- Bean Validation — `@Valid`, `@Validated`, custom validator
- Content negotiation

### Spring WebFlux (Reactive)
- Mono / Flux, Backpressure
- R2DBC แทน JDBC
- WebClient แทน RestTemplate
- เมื่อไหร่ควรใช้ (ไม่ใช่ทุก project)

### Documentation
- OpenAPI 3 (springdoc) — generate Swagger UI
- Postman collection จาก OpenAPI

---

## Phase 4: Security (เดือน 4-5) — ห้ามข้าม

### Spring Security
- Filter chain — เข้าใจลำดับการทำงาน
- `SecurityFilterChain` (config แบบใหม่)
- Authentication vs Authorization
- **JWT** — access + refresh token, rotation, blacklist
- **OAuth 2.0 / OIDC** — authorization server, resource server
- CORS ถูก (ไม่ใช่ `*` ทุกที่)
- CSRF — เมื่อไหร่เปิด/ปิด
- Method security — `@PreAuthorize`, `@PostAuthorize`

### Security Hardening
- **OWASP Top 10**
- Rate limiting — Bucket4j
- Password hashing — BCrypt, Argon2
- Secret management — Vault, AWS Secrets Manager
- SQL injection — parameterized query ทุกที่
- XSS, CSRF, SSRF, IDOR

---

## Phase 5: Testing (เดือน 5)

### Test Layers
- Unit test — JUnit 5 + Mockito + AssertJ
- Slice test — `@WebMvcTest`, `@DataJpaTest`, `@JsonTest`
- Integration test — `@SpringBootTest`
- **Testcontainers** — Postgres/Redis/Kafka จริงใน test
- MockMvc vs WebTestClient
- Contract testing — Spring Cloud Contract, Pact

### Advanced Testing
- Mutation testing — PIT
- Property-based testing — jqwik
- Load testing — JMeter, Gatling, k6
- Architecture testing — **ArchUnit** (บังคับ layer ไม่ให้ข้าม)

---

## Phase 6: Performance & JVM (เดือน 6-7)

### JVM Internals
- GC algorithms — G1, ZGC, Shenandoah
- Heap tuning — `-Xms`, `-Xmx`
- Memory leak debug — JProfiler, VisualVM, async-profiler
- Thread dump / Heap dump analysis (Eclipse MAT)
- JFR (Java Flight Recorder)

### Spring Boot Performance
- Connection pool tuning — HikariCP
- JPA — `hibernate.jdbc.batch_size`, `order_inserts`
- Caching — `@Cacheable`, Caffeine, Redis
- HTTP server tuning — Tomcat / Undertow
- Startup time — Spring Boot 3 + **GraalVM Native Image**
- AOT compilation

### Database Performance
- Index — B-tree, GIN, composite, partial
- `EXPLAIN ANALYZE` อ่านเป็น
- Connection pool sizing — `connections = ((core_count * 2) + effective_spindle_count)`
- Read replica + routing

---

## Phase 7: Distributed Systems (เดือน 7-9)

### Messaging
- Kafka — partition, consumer group, offset, exactly-once
- RabbitMQ — exchange types, DLQ
- Spring Cloud Stream
- **Outbox Pattern** — transactional message publish
- Idempotent consumer

### Resilience (Resilience4j)
- Circuit Breaker (closed/open/half-open)
- Retry + Exponential Backoff + Jitter
- Rate Limiter
- Bulkhead — แยก thread pool
- Time Limiter

### Distributed Patterns
- **Saga** — choreography vs orchestration
- **Event Sourcing**
- **CQRS** — Axon Framework
- Distributed lock — Redisson
- Distributed cache — Redis cluster

### Microservices
- API Gateway — Spring Cloud Gateway
- Service Discovery — Eureka, Consul
- Config server
- Distributed tracing — Micrometer Tracing + Zipkin/Tempo

---

## Phase 8: Observability (เดือน 9)

### Three Pillars
| Pillar | Tool |
|---|---|
| Metrics | Micrometer + Prometheus + Grafana |
| Logs | Logback/Log4j2 + ELK / Loki |
| Traces | OpenTelemetry + Jaeger / Tempo |

### Logging Best Practices
- Structured logging — JSON
- Correlation ID / Trace ID ใน MDC
- Log levels ถูก — ERROR/WARN/INFO/DEBUG
- PII scrubbing — ห้าม log password, credit card

### SLI / SLO / SLA
- กำหนด SLI (เช่น p99 latency < 500ms)
- Error budget
- Alert ที่ actionable

---

## Phase 9: Architecture & DDD (เดือน 10-12)

### Architecture Styles
- Layered (เริ่มจากนี่)
- **Hexagonal / Ports & Adapters** (สำคัญมาก)
- Clean Architecture
- Onion Architecture
- **Modular Monolith** — ก่อนจะ microservices

### Domain-Driven Design
- Strategic — Bounded Context, Context Map, Ubiquitous Language
- Tactical — Entity, Value Object, Aggregate, Domain Service, Domain Event
- Anti-corruption layer

### Code Quality
- SOLID เข้าใจจริง
- Refactoring (Martin Fowler)
- Code smells
- Static analysis — SonarQube, SpotBugs, Error Prone

---

## E-Commerce Specific

หัวข้อเพิ่มเติมที่ expert e-commerce dev ต้องรู้:

1. **Money handling** — `BigDecimal` (อย่าใช้ `double`), JSR-354
2. **Inventory management** — reserve/release pattern, oversell prevention
3. **Payment integration** — webhook, 3DS, idempotency
4. **Tax calculation** — VAT, regional
5. **Multi-currency** — exchange rate, store original currency
6. **Coupon engine** — rule engine pattern
7. **Search** — Elasticsearch / OpenSearch
8. **Recommendation** — collaborative filtering
9. **Order state machine** — Spring Statemachine
10. **GDPR compliance** — soft delete, anonymization

---

## DevOps & Production

- **Docker** + multi-stage build
- **Kubernetes** — Deployment, Service, ConfigMap, Secret
- **Helm chart**
- **CI/CD** — GitHub Actions / GitLab CI / Jenkins
- Zero-downtime deployment — rolling, blue-green, canary
- Feature flags — Unleash, LaunchDarkly
- **GraalVM Native Image**
- Database migration — **Flyway / Liquibase** (zero-downtime migration)

---

## Recommended Resources

### หนังสือ
1. *Spring in Action* (Craig Walls) — เริ่มจากนี่
2. *Effective Java* (Joshua Bloch) — ห้ามข้าม
3. *Java Concurrency in Practice*
4. *High-Performance Java Persistence* (Vlad Mihalcea) — JPA bible
5. *Domain-Driven Design* (Eric Evans) — blue book
6. *Implementing DDD* (Vaughn Vernon) — red book
7. *Building Microservices* (Sam Newman)
8. *Designing Data-Intensive Applications* (Kleppmann)

### Blog / Site
- **Baeldung** — Spring tutorial ดีที่สุด
- **vladmihalcea.com** — JPA/Hibernate expert
- **reflectoring.io** — deep dive
- **Spring official blog**
- **InfoQ**

### YouTube
- Spring I/O
- Devoxx
- GOTO conferences

---

## Projects ที่จะทำให้เป็น Expert

ทำตามลำดับ ห้ามข้าม:

### 1. E-commerce Monolith (2-3 เดือน) ← เริ่มที่นี่
- Hexagonal architecture
- JWT auth + role-based
- Product/Cart/Order/Payment
- PostgreSQL + Flyway
- Redis cache
- Testcontainers test
- OpenAPI doc

### 2. + Resilience (+1 เดือน)
- Payment gateway integration (Stripe sandbox)
- Webhook + idempotency
- Resilience4j
- Outbox pattern
- Distributed lock (inventory reserve)

### 3. + Async & Events (+1 เดือน)
- Kafka integration
- Event-driven (order events)
- Email/SMS notification consumer
- `@TransactionalEventListener`

### 4. + Observability (+2 สัปดาห์)
- Micrometer + Prometheus
- Grafana dashboard
- Distributed tracing
- Structured logging
- SLO/SLI

### 5. + Performance (+1 เดือน)
- Load test ด้วย k6 / Gatling
- Profile + tune (HikariCP, JPA batch, cache)
- N+1 hunting
- DB index optimization

### 6. Microservices Refactor (+2-3 เดือน)
- แยกเป็น service: catalog, order, payment, inventory, notification
- Spring Cloud Gateway
- Service discovery
- Saga (orchestration / choreography)

### 7. Production Deployment (+2 สัปดาห์)
- Docker + multi-stage
- Kubernetes
- CI/CD pipeline
- Zero-downtime deploy
- GraalVM native image

---

## Timeline Summary

| Phase | Duration | Outcome |
|---|---|---|
| 1-2 | เดือน 1-3 | Spring core + JPA solid |
| 3-5 | เดือน 4-6 | API + Security + Testing เก่ง |
| 6-7 | เดือน 7-9 | Performance + Distributed |
| 8-9 | เดือน 10-12 | Observability + Architecture |
| Practice | ตลอด | Build projects, OSS contribution |

---

## หลักการสำคัญ

1. **อย่าเรียนทฤษฎีเฉยๆ** — ทำ project ทุก concept
2. **อ่านโค้ด Spring เอง** — `spring-projects/spring-framework` บน GitHub
3. **Contribute Open Source**
4. **เขียน blog** — สอนคนอื่น = เข้าใจจริง
5. **ตามคน expert** — Josh Long, Vlad Mihalcea, Marco Behler, Thomas Vitale
6. **เน้น depth มากกว่า breadth** ใน 6 เดือนแรก
7. **ทำ side project ที่ scale จริง** — load test, monitor จริง
