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

# Health check
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run entrypoint
ENTRYPOINT ["/app/entrypoint.sh"]
