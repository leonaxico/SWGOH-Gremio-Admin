# Build context must be the repo root (see docker-compose.yml) so the
# multi-module Maven reactor can see the parent pom and both modules.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY backend backend
COPY frontend frontend
RUN mvn -pl frontend -am clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/frontend/target/frontend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
