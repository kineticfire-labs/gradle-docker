# Dockerfile for app-server (scenario-8)
# Purpose: Demonstrates multi-image build with Repository Mode naming
# Features: Uses build arg, NO labels (tests zero labels scenario)

FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy application JAR using build arg
ARG JAR_FILE
COPY ${JAR_FILE} app.jar

# Note: NO LABEL instructions in this Dockerfile
# This tests the scenario where an image has zero labels from Dockerfile
# and zero labels from DSL configuration

# Echo build arg to ensure it's recorded in docker history
# (Docker only records build args in history when they're used in RUN commands)
RUN echo "Built with JAR_FILE=${JAR_FILE}"

# Expose port
EXPOSE 8080

# Set environment variables
ENV PORT=8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
