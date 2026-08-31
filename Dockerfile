FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --configuration runtimeClasspath --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S routeplan && adduser -S routeplan -G routeplan
COPY --from=builder --chown=routeplan:routeplan /workspace/build/libs/routeplan-0.0.1-SNAPSHOT.jar app.jar
USER routeplan
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
