# Dockerfile for database-app example
# Demonstrates Spring Boot + PostgreSQL integration testing

FROM eclipse-temurin:17-jre-alpine

# Install wget for health check
RUN apk add --no-cache wget

WORKDIR /app

# Copy JAR file
COPY database-app-1.0.0.jar app.jar

# Expose application port
EXPOSE 8080

# Start application
ENTRYPOINT ["java", "-jar", "app.jar"]
