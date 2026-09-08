# -----------------------------------------------------------
# Stage 1: Build JAR with Maven & Eclipse Temurin JDK 21
# -----------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build project
COPY src ./src
RUN mvn clean package -DskipTests -B

# -----------------------------------------------------------
# Stage 2: Production JRE 21 Runtime (Minimal & Hardened)
# -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="Custos Platform Team <ops@custos.io>"
LABEL description="Custos Server Automation & Pipeline Orchestration Platform"

# Install required client binaries and utility tools (including MariaDB Connector/C plugins for MySQL 8 caching_sha2_password)
RUN apk update && apk add --no-cache \
    curl \
    bash \
    postgresql16-client \
    mariadb-client \
    mariadb-connector-c \
    tar \
    gzip \
    zstd \
    tzdata && \
    mkdir -p /etc/my.cnf.d && \
    printf '[client-mariadb]\nssl-verify-server-cert=0\n' > /etc/my.cnf.d/ssl.cnf && \
    rm -rf /var/cache/apk/*

# Setup system user and group (Non-root UID/GID: 10001)
RUN addgroup -g 10001 custos && \
    adduser -u 10001 -G custos -s /bin/sh -D custos

# Setup application and staging directories
RUN mkdir -p /app /var/custos/staging && \
    chown -R custos:custos /app /var/custos

WORKDIR /app

# Copy compiled jar from builder
COPY --from=builder --chown=custos:custos /build/target/custos-backend-*.jar /app/app.jar

# Switch to unprivileged user
USER custos

# Environment variables default configuration
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Healthcheck using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
