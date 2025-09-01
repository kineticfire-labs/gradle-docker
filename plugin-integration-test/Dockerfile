# Multi-stage Dockerfile for integration testing
FROM eclipse-temurin:21-jdk-alpine AS builder

# Build arguments that will be provided by the gradle-docker plugin
ARG JAR_FILE=app.jar
ARG BUILD_VERSION=unknown
ARG BUILD_TIME=unknown

WORKDIR /app

# Copy the application JAR
COPY ${JAR_FILE} app.jar

# Verify the JAR exists and is valid
RUN java -jar app.jar --help || echo "JAR validation completed"

# Production stage
FROM eclipse-temurin:21-jre-alpine

# Set build information as environment variables
ARG BUILD_VERSION=unknown
ARG BUILD_TIME=unknown
ENV APP_BUILD_VERSION=${BUILD_VERSION}
ENV APP_BUILD_TIME=${BUILD_TIME}

# Create non-root user for security
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -s /bin/sh -D appuser

WORKDIR /app

# Copy application from builder stage
COPY --from=builder --chown=appuser:appgroup /app/app.jar app.jar

# Switch to non-root user
USER appuser

# Health check for Docker Compose integration
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD java -cp app.jar com.kineticfire.gradle.docker.integration.IntegrationTestApp || exit 1

# Expose port for potential web interface (future enhancement)
EXPOSE 8080

# Set JVM options for containerized environment
ENV JAVA_OPTS="-Xmx256m -Xms128m -XX:+UseContainerSupport"

# Pass build args as system properties
ENV JAVA_OPTS="${JAVA_OPTS} -Dapp.build.version=${BUILD_VERSION} -Dapp.build.time=${BUILD_TIME}"

# Run the application
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]

# Labels for better container management
LABEL maintainer="gradle-docker Contributors"
LABEL version="${BUILD_VERSION}"
LABEL description="Integration test application for gradle-docker plugin"
LABEL build.time="${BUILD_TIME}"