FROM openjdk:17-jdk-slim

WORKDIR /app

COPY build/libs/*.jar app.jar
COPY service-account-key.json /app/service-account-key.json

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]