FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy jar file
COPY target/user-service-1.0.0-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8081/api/users/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]










WORKDIR /app

# Copy jar file
COPY target/user-service-1.0.0-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8081/api/users/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]











