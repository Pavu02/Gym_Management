# Stage 1: Build application with Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and dependency definitions first for caching
COPY pom.xml .
COPY src ./src

# Build production jar without running tests
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image with Temurin JRE 17
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy packaged jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose backend port
EXPOSE 8080

# Run Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
