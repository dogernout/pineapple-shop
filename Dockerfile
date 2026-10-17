# ---------- build ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle* settings.gradle* ./
COPY src src

# cache mount сохраняет скачанные зависимости между сборками
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# ---------- runtime ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --no-create-home appuser
COPY --from=build /workspace/build/libs/*.jar app.jar
USER appuser

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
