FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 durableflow
WORKDIR /app
COPY --from=build /workspace/target/durableflow-*.jar app.jar
USER durableflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
