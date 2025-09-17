# Multi-stage build for API Gateway Service
FROM maven:3.9-amazoncorretto-17 AS builder

WORKDIR /app

# Copy parent POM and all module POMs
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/
COPY shared-lib/pom.xml shared-lib/
COPY user-service/pom.xml user-service/
COPY risk-monitoring-service/pom.xml risk-monitoring-service/
COPY position-service/pom.xml position-service/
COPY notification-service/pom.xml notification-service/

# Copy shared-lib source (dependency)
COPY shared-lib/src shared-lib/src

# Copy api-gateway source
COPY api-gateway/src api-gateway/src

# Build the application with Spring Boot repackage
RUN mvn clean package spring-boot:repackage -DskipTests -pl api-gateway -am

# Runtime stage
FROM amazoncorretto:17-alpine

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/api-gateway/target/api-gateway-*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]