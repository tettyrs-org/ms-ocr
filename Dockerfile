# Multi-stage build untuk ms-ocr (correction service)
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
COPY src ./src

# Build application
RUN sed -i 's/\r$//' ./mvnw && chmod +x ./mvnw && ./mvnw clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy entire Quarkus app (includes JAR + lib + app directories)
COPY --from=builder /app/target/quarkus-app/ ./

# Expose port (Quarkus default)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD java -cp app.jar org.tettyrs.msocr.health.HealthCheck || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
CMD ["-Dquarkus.http.host=0.0.0.0"]
