# syntax=docker/dockerfile:1.7
# Ein Dockerfile für alle Services:  docker build --build-arg SERVICE=game-server -t u-boot-arcade/game-server .

FROM eclipse-temurin:21-jdk AS build
ARG SERVICE
WORKDIR /src
# Gleiche Gradle-Version wie lokal: über den Wrapper
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY game-core game-core
COPY physics-service physics-service
COPY game-server game-server
COPY leaderboard leaderboard
COPY enemy-ai enemy-ai
COPY player-swarm player-swarm
RUN --mount=type=cache,target=/root/.gradle,id=uboot-gradle \
    ./gradlew --no-daemon -q ":${SERVICE}:bootJar" -x test \
 && cp "${SERVICE}/build/libs/${SERVICE}-1.0.0.jar" /app.jar

FROM eclipse-temurin:21-jre
ARG OTEL_AGENT_VERSION=2.32.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
COPY --from=build /app.jar /app/app.jar
RUN chmod 644 /otel/opentelemetry-javaagent.jar && useradd --system --uid 10001 app
USER 10001
# Zero-Code-Instrumentierung: der Agent erzeugt HTTP-, JDBC- und JVM-Telemetrie automatisch
# und exportiert zusätzlich alles, was der Code über die OTel-API selbst meldet.
ENV JAVA_TOOL_OPTIONS="-javaagent:/otel/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
