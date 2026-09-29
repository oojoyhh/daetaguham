FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/build/libs/*.jar app.jar

USER app
EXPOSE 8080
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:MaxRAMPercentage=75.0 -jar /app/app.jar"]
