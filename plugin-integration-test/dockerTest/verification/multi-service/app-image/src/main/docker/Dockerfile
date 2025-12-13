FROM eclipse-temurin:17-jre-alpine

# Install wget for health checks
RUN apk add --no-cache wget

WORKDIR /app

# Copy JAR file
ARG JAR_FILE
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
