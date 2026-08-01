# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
# Limit Maven heap during `mvn package` so small hosts (e.g. Lightsail 1GB) are less likely to lock up.
# Override: docker compose build api --build-arg MAVEN_HEAP=512m
ARG MAVEN_HEAP=768m
# Retry transient Maven Central failures (TCP resets, timeouts). Recoverable on a second attempt without re-running the
# whole build, especially when combined with the BuildKit ~/.m2 cache mount below.
ENV MAVEN_OPTS="-Xmx${MAVEN_HEAP}"
ENV MAVEN_CLI_OPTS="--batch-mode --no-transfer-progress \
  -Dmaven.wagon.http.retryHandler.count=6 \
  -Dmaven.wagon.http.retryHandler.requestSentEnabled=true \
  -Dmaven.wagon.http.pool=false \
  -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
  -Dmaven.wagon.http.connectionTimeout=60000 \
  -Dmaven.wagon.http.readTimeout=120000 \
  -Daether.connector.http.connectionMaxTtl=120 \
  -Daether.connector.http.retryHandler.count=6 \
  -Daether.connector.resumeDownloads=true"

WORKDIR /workspace

# Resolve dependencies in their own layer, with a BuildKit cache mount on ~/.m2 so previously-fetched JARs survive
# subsequent rebuilds. After this step, the package step almost never hits the network.
COPY server/pom.xml server/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f server/pom.xml ${MAVEN_CLI_OPTS} \
        -Dmaven.test.skip=true \
        dependency:go-offline

COPY server/src server/src

# Skip compiling and running tests (faster than -DskipTests alone, which still compiles src/test/java).
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f server/pom.xml ${MAVEN_CLI_OPTS} \
        -Dmaven.test.skip=true \
        package

FROM eclipse-temurin:21-jre
WORKDIR /app
# curl for health checks; ffmpeg compresses/splits oversized Just Press Record clips for Whisper (25MB cap);
# libheif-examples (heif-convert) turns Apple HEIC/HEIF uploads into JPEG for browser display.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ffmpeg libheif-examples \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/server/target/tracker-pg-server-*.jar /app/app.jar

EXPOSE 9091

ENTRYPOINT ["java", "-Djdk.httpclient.enableExpectContinue=false", "-jar", "/app/app.jar"]
