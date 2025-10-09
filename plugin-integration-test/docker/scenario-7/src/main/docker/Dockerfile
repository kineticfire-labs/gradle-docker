# Minimal Dockerfile for scenario-7 integration test
# Tests zero build args and zero labels
FROM eclipse-temurin:21-jre-alpine

# Copy the application JAR
COPY app-1.0.0.jar /app/app.jar

# Expose port for the time server
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
