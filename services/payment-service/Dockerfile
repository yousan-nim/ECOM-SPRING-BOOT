# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY services/user-service/pom.xml    services/user-service/pom.xml
COPY services/catalog-service/pom.xml services/catalog-service/pom.xml
COPY services/order-service/pom.xml   services/order-service/pom.xml
COPY services/payment-service/pom.xml services/payment-service/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl services/payment-service -am dependency:go-offline

COPY services/payment-service/src services/payment-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl services/payment-service -am -DskipTests package \
 && cp services/payment-service/target/payment-service.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system --gid 1001 app \
 && useradd  --system --uid 1001 --gid 1001 --shell /usr/sbin/nologin app \
 && apt-get update && apt-get install --no-install-recommends -y wget \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/app.jar /app/app.jar
USER app
EXPOSE 8083
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8083/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
