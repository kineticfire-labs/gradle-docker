# Integration test scenario-4: Empty publish target inheritance test
FROM eclipse-temurin:21-jre

# Create app directory
RUN mkdir /app

# Copy application JAR
COPY app-*.jar /app/app.jar

# Set working directory
WORKDIR /app

# Expose port
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "app.jar"]