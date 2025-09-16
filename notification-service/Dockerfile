# Build stage
FROM maven:3.9-amazoncorretto-17 AS builder
WORKDIR /app

# Copy parent pom first
COPY pom.xml /app/parent-pom.xml

# Copy service pom and source
COPY notification-service/pom.xml /app/pom.xml
COPY notification-service/src /app/src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM amazoncorretto:17-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1000 appgroup && \
    adduser -D -u 1000 -G appgroup appuser

# Copy JAR from build stage
COPY --from=builder /app/target/notification-service-*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1

# Expose port
EXPOSE 8084

# Run the application
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]