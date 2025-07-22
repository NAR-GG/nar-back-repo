FROM openjdk:17-jdk-slim

ENV TZ=Asia/Seoul

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]