FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace

COPY backend-api/settings.gradle.kts settings.gradle.kts
COPY backend-api/build.gradle.kts build.gradle.kts
COPY backend-api/src src

RUN gradle --no-daemon bootJar \
    && find build/libs -maxdepth 1 -type f -name "*.jar" ! -name "*-plain.jar" -exec cp {} /tmp/app.jar \;

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /tmp/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
