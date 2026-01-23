# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /source
COPY pom.xml .
COPY setup/local/Tester/pom.xml setup/local/Tester/pom.xml
COPY setup/local/Tester setup/local/Tester
COPY src/toolkit-core/pom.xml src/toolkit-core/pom.xml
COPY src/toolkit-core src/toolkit-core
RUN mvn -B -f pom.xml -pl setup/local/Tester -am clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /source/setup/local/Tester/target/tester-*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
