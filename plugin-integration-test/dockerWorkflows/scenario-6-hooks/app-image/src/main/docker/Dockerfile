# Dockerfile for workflow scenario 6: Hooks and Customization
#
# This image is used to test the dockerWorkflows hook system:
# - beforeBuild, afterBuild hooks
# - beforeTest, afterTest hooks
# - afterSuccess, afterFailure hooks
#
# The application exposes a simple time service that can be tested to verify
# the complete pipeline workflow including hook execution.

FROM eclipse-temurin:21-jre-alpine

# Create non-root user for security best practices
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

# Copy the application JAR
COPY app.jar /app/app.jar

# Change ownership to the app user
RUN chown -R app:app /app

# Switch to non-root user
USER app

# Expose the application port
EXPOSE 8080

# Configure health check for compose wait functionality
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Start the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
