# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy just the wrapper/build files first so dependency resolution is
# cached in its own layer, independent of source changes.
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN ./gradlew --version

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime stage -----------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
