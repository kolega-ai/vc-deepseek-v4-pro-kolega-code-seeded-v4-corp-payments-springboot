# Multi-stage build for a Spring Boot tier.
# Usage: docker build -f docker/<name>.Dockerfile --build-arg MODULE=<module> .
ARG MODULE
FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /build
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
COPY gradle/ gradle/
COPY ${MODULE}/build.gradle ${MODULE}/build.gradle
COPY ${MODULE}/src ${MODULE}/src
RUN ./gradlew --no-daemon :${MODULE}:bootJar -x test

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /build/${MODULE}/build/libs/${MODULE}.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
