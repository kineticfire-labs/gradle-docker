FROM eclipse-temurin:21-jre

# Create app directory
WORKDIR /app

# Copy JAR file
COPY app.jar /app/app.jar

# Copy entrypoint script
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Expose port
EXPOSE 8080

# Set entrypoint
ENTRYPOINT ["/app/entrypoint.sh"]
