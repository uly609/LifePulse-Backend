FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/lifepulse-backend-0.0.1-SNAPSHOT.jar /app/lifepulse-backend.jar

EXPOSE 8110
ENTRYPOINT ["java", "-jar", "/app/lifepulse-backend.jar", "--spring.profiles.active=docker"]
