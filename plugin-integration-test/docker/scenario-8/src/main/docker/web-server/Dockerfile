# Dockerfile for web-server (scenario-8)
# Purpose: Demonstrates multi-image build with different configuration
# Features: Uses build arg, has label from Dockerfile

FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy application JAR
ARG VERSION
COPY app-1.0.0.jar app.jar

# Add metadata label from Dockerfile
# This label will be combined with DSL-configured labels during build
LABEL app-version="${VERSION}"

# Echo build arg to ensure it's recorded in docker history
# (Docker only records build args in history when they're used in RUN commands)
RUN echo "Built with VERSION=${VERSION}"

# Expose different port than app-server
EXPOSE 9090

# Set environment variables
ENV PORT=9090

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
