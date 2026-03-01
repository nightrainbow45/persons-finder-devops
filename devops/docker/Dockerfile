# Multi-stage Docker build for Persons Finder Spring Boot application
# Stage 1: Build stage
FROM gradle:7.6.4-jdk11-focal AS builder

WORKDIR /app

# Copy Gradle configuration files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application
RUN gradle clean build -x test --no-daemon

# Stage 2: Runtime stage
FROM eclipse-temurin:11.0.26_4-jre-alpine

# Upgrade Alpine packages to patch OS-level CVEs (gnutls, libpng, etc.)
RUN apk update && apk upgrade --no-cache

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
