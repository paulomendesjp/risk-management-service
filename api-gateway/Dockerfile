# Build stage
FROM maven:3.9-amazoncorretto-17 AS builder
WORKDIR /app

# Copy parent pom
COPY pom.xml /app/

# Copy all modules (needed for multi-module build)
COPY shared-lib/pom.xml /app/shared-lib/
COPY shared-lib/src /app/shared-lib/src

COPY api-gateway/pom.xml /app/api-gateway/
COPY api-gateway/src /app/api-gateway/src
COPY user-service/pom.xml /app/user-service/
COPY position-service/pom.xml /app/position-service/
COPY risk-monitoring-service/pom.xml /app/risk-monitoring-service/
COPY notification-service/pom.xml /app/notification-service/

# Build only what we need
WORKDIR /app
RUN mvn clean package spring-boot:repackage -DskipTests -pl api-gateway -am

# Runtime stage
FROM amazoncorretto:17-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1000 appgroup && \
    adduser -D -u 1000 -G appgroup appuser

# Copy JAR from build stage
COPY --from=builder /app/api-gateway/target/api-gateway-*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]
