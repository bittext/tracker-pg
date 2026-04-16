FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY server/pom.xml server/pom.xml
COPY server/src server/src

RUN mvn -f server/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/server/target/tracker-pg-server-*.jar /app/app.jar

EXPOSE 9091

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
