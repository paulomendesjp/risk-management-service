# Build stage
FROM maven:3.9-amazoncorretto-17 AS builder
WORKDIR /app

# Copy parent pom
COPY pom.xml /app/

# Copy all modules (needed for multi-module build)
COPY shared-lib/pom.xml /app/shared-lib/
COPY shared-lib/src /app/shared-lib/src

COPY api-gateway/pom.xml /app/api-gateway/
COPY user-service/pom.xml /app/user-service/
COPY position-service/pom.xml /app/position-service/
COPY risk-monitoring-service/pom.xml /app/risk-monitoring-service/
COPY notification-service/pom.xml /app/notification-service/

# Copy source for this service
COPY risk-monitoring-service/src /app/risk-monitoring-service/src

# Build only what we need
WORKDIR /app
RUN mvn clean install -DskipTests -pl risk-monitoring-service -am

# Runtime stage
FROM amazoncorretto:17-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1000 appgroup && \
    adduser -D -u 1000 -G appgroup appuser

# Copy JAR from build stage
COPY --from=builder /app/risk-monitoring-service/target/risk-monitoring-service-*.jar app.jar

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
