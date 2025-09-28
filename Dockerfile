# Multi-stage build for optimized production image
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Create non-root user for build
RUN groupadd -r build && useradd -r -g build build

WORKDIR /app

# Copy dependency files first for better caching
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Production stage
FROM eclipse-temurin:21-jre-alpine

# Install security updates and create non-root user
RUN apk update && apk upgrade && \
    addgroup -S debbly && adduser -S debbly -G debbly

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY --from=builder --chown=debbly:debbly /app/target/server-0.0.1-SNAPSHOT.jar app.jar

# Switch to non-root user
USER debbly

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Start the app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

#  docker buildx build --platform linux/amd64 -t debbly-api:latest .
#  docker save debbly-api:latest -o debbly-api.tar
#  scp debbly-api.tar root@178.156.183.33:/opt/services