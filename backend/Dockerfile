FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S opsflow && adduser -S opsflow -G opsflow
WORKDIR /app
COPY --from=build /app/target/opsflow-1.0.0.jar app.jar
USER opsflow
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
