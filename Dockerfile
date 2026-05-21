FROM eclipse-temurin:21-jdk

ENV TZ=Asia/Seoul
ENV SPRING_PROFILES_ACTIVE=prod

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
