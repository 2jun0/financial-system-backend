FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

ARG APP_DIR
COPY ${APP_DIR}/gradlew ${APP_DIR}/build.gradle.kts ${APP_DIR}/settings.gradle.kts ./
COPY ${APP_DIR}/gradle ./gradle
COPY ${APP_DIR}/src ./src
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
