FROM maven:3.9.9-eclipse-temurin-17 AS build
# Limit Maven heap during `mvn package` so small hosts (e.g. Lightsail 1GB) are less likely to lock up.
# Override: docker compose build api --build-arg MAVEN_HEAP=512m
ARG MAVEN_HEAP=768m
ENV MAVEN_OPTS="-Xmx${MAVEN_HEAP}"

WORKDIR /workspace

COPY server/pom.xml server/pom.xml
COPY server/src server/src

RUN mvn -f server/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/server/target/tracker-pg-server-*.jar /app/app.jar

EXPOSE 9091

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
