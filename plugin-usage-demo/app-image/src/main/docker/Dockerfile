# Dockerfile for TimeServerApp demonstration
# Purpose: Demonstrates containerization of Java application built by gradle-docker plugin

FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy application JAR (will be copied by copyAppJar task)
ARG JAR_FILE
COPY ${JAR_FILE} app.jar

# Add build metadata
ARG BUILD_VERSION
ARG BUILD_TIME
LABEL version="${BUILD_VERSION}" build-time="${BUILD_TIME}"

# Health check endpoint
HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Install curl for health check
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Expose port
EXPOSE 8080

# Set environment variables
ENV PORT=8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]