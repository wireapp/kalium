# Multi-stage Dockerfile for Kalium
# Stage 1: Build environment with all dependencies

FROM eclipse-temurin:21-jdk AS builder

# Install required system dependencies
RUN apt-get update && apt-get install -y \
    git \
    curl \
    unzip \
    make \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy project files
COPY . .

# Make gradlew executable
RUN chmod +x ./gradlew

# Build native libraries (AVS)
RUN make || echo "Native libraries build skipped"

# Build the project (skip tests for faster builds, can be enabled if needed)
RUN ./gradlew clean build -x test --no-daemon

# Build CLI application
RUN ./gradlew :sample:cli:assemble --no-daemon

# Stage 2: Runtime environment (minimal)
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install runtime dependencies
RUN apk add --no-cache \
    bash \
    curl

# Create non-root user
RUN addgroup -g 1000 kalium && \
    adduser -D -u 1000 -G kalium kalium

# Set working directory
WORKDIR /app

# Copy built artifacts from builder
COPY --from=builder /app/sample/cli/build/libs/*.jar /app/cli.jar
COPY --from=builder /app/native/libs /app/native/libs

# Change ownership to non-root user
RUN chown -R kalium:kalium /app

# Switch to non-root user
USER kalium

# Set library path for native libraries
ENV JAVA_LIBRARY_PATH=/app/native/libs

# Expose any necessary ports (if running a server component)
# EXPOSE 8080

# Default command (can be overridden)
CMD ["java", "-Djava.library.path=/app/native/libs", "-jar", "/app/cli.jar", "--help"]

# Stage 3: Development environment (optional, for building/testing)
FROM builder AS development

# Install additional development tools
RUN apt-get update && apt-get install -y \
    vim \
    nano \
    htop \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Keep the full build context
CMD ["/bin/bash"]
