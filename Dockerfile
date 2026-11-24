FROM gradle:8.14.1-jdk21 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle gradlew build.gradle.kts gradle.properties settings.gradle.kts ./
COPY --chown=gradle:gradle gradle/libs.versions.toml gradle/
COPY --chown=gradle:gradle platform platform
COPY --chown=gradle:gradle security security
COPY --chown=gradle:gradle alert alert
COPY --chown=gradle:gradle audit audit
COPY --chown=gradle:gradle ai ai
COPY --chown=gradle:gradle notify notify
COPY --chown=gradle:gradle app app
RUN gradle :app:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /home/gradle/src/app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
