# ─────────────────────────────────────────────────────────────────────────────
# Multi-stage Dockerfile for the banking backend
#
# Stage 1 (builder): Maven build with bank-core installed from GitHub Packages
# Stage 2 (runtime): Minimal JRE image with the fat jar
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy pom first — Docker cache layer for dependency downloads
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B --no-transfer-progress 2>/dev/null || true

# Copy source and build
COPY src ./src

RUN mvn clean package -DskipTests -B --no-transfer-progress

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

LABEL maintainer="Surf Digital Technologies <dev@surfdigital.ng>"
LABEL description="Banking Backend — modular monolith on bank-core"

# Non-root user for security
RUN addgroup -S banking && adduser -S banking -G banking

WORKDIR /app

# Copy the fat jar from builder stage
COPY --from=builder /build/target/banking-backend-*.jar app.jar

# Change ownership
RUN chown banking:banking app.jar

USER banking

# JVM tuning for containers:
# -XX:+UseContainerSupport          — respect Docker memory limits
# -XX:MaxRAMPercentage=75.0         — use up to 75% of container RAM for heap
# -XX:+UseG1GC                      — G1 GC, good for low-latency banking workloads
# -Djava.security.egd=file:/dev/./urandom — faster SecureRandom startup
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.profiles.active=docker"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
