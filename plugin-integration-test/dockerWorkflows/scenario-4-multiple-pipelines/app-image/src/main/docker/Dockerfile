# Dockerfile for workflow scenario 4 - Multiple Pipelines
# This image is used across all three pipelines (dev, staging, prod)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the JAR file
COPY app.jar /app/app.jar

# Expose port 8080 for the web service
EXPOSE 8080

# Health check for compose wait functionality
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
