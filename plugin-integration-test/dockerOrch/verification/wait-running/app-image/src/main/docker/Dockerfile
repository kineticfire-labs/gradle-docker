# Multi-stage build for efficient image
FROM eclipse-temurin:17-jre-alpine

# Create app user
RUN addgroup -S app && adduser -S app -G app

# Set working directory
WORKDIR /app

# Copy JAR (passed as build arg from Gradle)
ARG JAR_FILE
COPY ${JAR_FILE} app.jar

# Copy entrypoint script
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Change ownership
RUN chown -R app:app /app

# Switch to app user
USER app

# Expose port
EXPOSE 8080

# NO HEALTHCHECK - testing waitForRunning (running state, not healthy state)

# Run entrypoint
ENTRYPOINT ["/app/entrypoint.sh"]
