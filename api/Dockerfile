# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Cache dependencies
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw \
    && ./mvnw dependency:go-offline -B

# Build application
COPY src src
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy built jar
COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
