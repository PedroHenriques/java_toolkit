# syntax=docker/dockerfile:1.7

# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /source
COPY pom.xml .
COPY setup/local/Tester/pom.xml setup/local/Tester/pom.xml
COPY src/toolkit-core/pom.xml src/toolkit-core/pom.xml
# NOTE: For the local development environment, using cache on dependencies to speed build times.
#       DO NOT USE IN REMOTE ENVIRONMENTS
RUN --mount=type=cache,target=/root/.m2 mvn -B -f pom.xml -pl setup/local/Tester -am dependency:go-offline -DskipTests
COPY setup/local/Tester setup/local/Tester
COPY src/toolkit-core src/toolkit-core
# NOTE: For the local development environment, using cache on dependencies to speed build times.
#       DO NOT USE IN REMOTE ENVIRONMENTS
RUN --mount=type=cache,target=/root/.m2 mvn -B -f pom.xml -pl setup/local/Tester -am clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /source/setup/local/Tester/target/tester-*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
