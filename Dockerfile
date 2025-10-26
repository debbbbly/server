# Multi-stage build for optimized production image
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy dependency files first for better caching
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Production stage - Using debian-based image for Railway compatibility
FROM eclipse-temurin:21-jre-jammy

# Install curl for health checks
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r debbly && useradd -r -g debbly debbly

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY --from=builder --chown=debbly:debbly /app/target/server-0.0.1-SNAPSHOT.jar app.jar

# Switch to non-root user
USER debbly

# Health check - Railway uses $PORT env variable
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1

# Expose port (Railway will override with $PORT)
EXPOSE 8080

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Start the app - Use $PORT from Railway
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]