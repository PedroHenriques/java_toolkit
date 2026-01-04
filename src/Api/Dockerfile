# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /source
COPY pom.xml .
COPY src/Api/pom.xml src/Api/pom.xml
COPY src src
RUN mvn -pl src/Api clean package spring-boot:repackage -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /source/src/Api/target/api-service-*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
