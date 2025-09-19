# Build stage
FROM maven:3.9-amazoncorretto-17 AS builder
WORKDIR /app

# Copy parent pom
COPY pom.xml /app/

# Copy all module POMs first for better caching
COPY shared-lib/pom.xml /app/shared-lib/
COPY api-gateway/pom.xml /app/api-gateway/
COPY user-service/pom.xml /app/user-service/
COPY position-service/pom.xml /app/position-service/
COPY risk-monitoring-service/pom.xml /app/risk-monitoring-service/
COPY notification-service/pom.xml /app/notification-service/
COPY kraken-service/pom.xml /app/kraken-service/

# Copy shared-lib source (dependency)
COPY shared-lib/src /app/shared-lib/src

# Copy kraken-service source
COPY kraken-service/src /app/kraken-service/src

# Build only what we need
WORKDIR /app
RUN mvn clean package spring-boot:repackage -DskipTests -pl kraken-service -am

# Runtime stage
FROM amazoncorretto:17-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1000 appgroup && \
    adduser -D -u 1000 -G appgroup appuser

# Copy JAR from build stage
COPY --from=builder /app/kraken-service/target/kraken-service-*.jar app.jar

# Create logs directory and set ownership
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8086/actuator/health || exit 1

# Expose port (Kraken service uses 8086)
EXPOSE 8086

# Run the application with docker profile
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]