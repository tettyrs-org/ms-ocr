# Multi-stage build untuk ms-ocr (correction service)
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /app/target/ms-ocr-*-runner.jar app.jar

# Expose port (Quarkus default)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD java -cp app.jar org.tettyrs.msocr.health.HealthCheck || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["-Dquarkus.http.host=0.0.0.0"]
