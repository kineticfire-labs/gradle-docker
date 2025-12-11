# Dockerfile for dockerProject Scenario 5 - ContextDir Mode
#
# This Dockerfile is located in the pre-existing Docker context directory.
# The context directory (src/main/docker-context/) contains:
# - This Dockerfile
# - app.jar (copied by prepareContext task at build time)
#
# This demonstrates the contextDir mode where users provide their own
# fully-configured Docker build context.

FROM eclipse-temurin:21-jre-alpine

# Build argument for version tracking
ARG BUILD_VERSION=unknown

# Create non-root user for security
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

# Copy application JAR from context directory
# Note: app.jar is copied to context by prepareContext task
COPY app.jar /app/app.jar

# Set ownership
RUN chown -R app:app /app

# Switch to non-root user
USER app

# Expose application port
EXPOSE 8080

# Health check for container orchestration
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Image metadata
LABEL org.opencontainers.image.title="Project Scenario 5 - ContextDir Mode" \
      org.opencontainers.image.description="Demonstrates dockerProject DSL with contextDir"
