# Multi-stage build for a Spring Boot tier.
# Usage: docker build -f docker/<name>.Dockerfile --build-arg MODULE=<module> .
ARG MODULE
FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /build
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
COPY gradle/ gradle/
COPY core-mock/build.gradle core-mock/build.gradle
COPY core-mock/src core-mock/src
RUN ./gradlew --no-daemon :core-mock:bootJar -x test

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /build/core-mock/build/libs/core-mock.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
