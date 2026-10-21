# Multi-stage build for a Spring Boot tier.
# Usage: docker build -f docker/<name>.Dockerfile --build-arg MODULE=<module> .
ARG MODULE
FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /build
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
COPY gradle/ gradle/
COPY payment-api/build.gradle payment-api/build.gradle
COPY payment-api/src payment-api/src
RUN ./gradlew --no-daemon :payment-api:bootJar -x test

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /build/payment-api/build/libs/payment-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
