# syntax=docker/dockerfile:1.7
# ───────────────────────────────────────────────────────────────
# Build stage — builds the whole multi-module project, then keeps
# only this service's fat jar.
# Build context is the repo root (see docker-compose.yml).
# ───────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY services/user-service/pom.xml    services/user-service/pom.xml
COPY services/catalog-service/pom.xml services/catalog-service/pom.xml
COPY services/order-service/pom.xml   services/order-service/pom.xml
COPY services/payment-service/pom.xml services/payment-service/pom.xml

# Warm dependency cache.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl services/catalog-service -am dependency:go-offline

COPY services/catalog-service/src services/catalog-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl services/catalog-service -am -DskipTests package \
 && cp services/catalog-service/target/catalog-service.jar /workspace/app.jar

# ───────────────────────────────────────────────────────────────
# Runtime
# ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system --gid 1001 app \
 && useradd  --system --uid 1001 --gid 1001 --shell /usr/sbin/nologin app \
 && apt-get update && apt-get install --no-install-recommends -y wget \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/app.jar /app/app.jar
USER app
EXPOSE 8081
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
