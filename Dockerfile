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

RUN groupadd -r app && useradd -r -g app app && mkdir -p /app/logs && chown app:app /app/logs

WORKDIR /app

# Copy entire Quarkus app (includes JAR + lib + app directories)
COPY --from=builder /app/target/quarkus-app/ ./

# Expose port (Quarkus default)
USER app

EXPOSE 8080

# Tanpa HEALTHCHECK: ms-ocr tidak punya endpoint HTTP yang dipakai pemeriksaan, dan percobaan
# sebelumnya memanggil kelas yang tidak ada sehingga container selalu unhealthy.

# Run application
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
CMD ["-Dquarkus.http.host=0.0.0.0"]
