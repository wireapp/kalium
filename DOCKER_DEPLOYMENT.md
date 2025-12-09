# Kalium Docker Deployment Guide

This guide explains how to build and deploy Kalium using Docker and Docker Compose.

## Prerequisites

- Docker 20.10 or later
- Docker Compose 2.0 or later
- At least 4GB of available memory for building

## Quick Start

### Build and Run CLI Application

```bash
# Build the Docker image
docker-compose build kalium-cli

# Run the CLI application
docker-compose run --rm kalium-cli

# Run with custom command
docker-compose run --rm kalium-cli java -Djava.library.path=/app/native/libs \
    -jar /app/cli.jar login --email user@example.com --password yourpassword
```

### Development Environment

```bash
# Start development container
docker-compose run --rm kalium-dev

# Inside the container, you can run:
./gradlew build                    # Build the project
./gradlew jvmTest                  # Run tests
./gradlew :sample:cli:assemble     # Build CLI
```

## Available Services

### kalium-cli
The runtime environment with the compiled CLI application.

```bash
# Start the CLI service
docker-compose up -d kalium-cli

# View logs
docker-compose logs -f kalium-cli

# Stop the service
docker-compose down kalium-cli
```

### kalium-dev
Full development environment with source code mounted.

```bash
# Start interactive development shell
docker-compose run --rm kalium-dev /bin/bash

# Run specific Gradle tasks
docker-compose run --rm kalium-dev ./gradlew clean build

# Run tests
docker-compose run --rm kalium-dev ./gradlew jvmTest \
    -Djava.library.path=./native/libs
```

### postgres (Optional)
PostgreSQL database for sync outbox backend implementation.

```bash
# Start PostgreSQL
docker-compose up -d postgres

# Connect to database
docker-compose exec postgres psql -U kalium -d kalium_sync

# Check health
docker-compose ps postgres
```

### redis (Optional)
Redis for caching and session management.

```bash
# Start Redis
docker-compose up -d redis

# Connect to Redis CLI
docker-compose exec redis redis-cli

# Check health
docker-compose ps redis
```

## Building Individual Images

### Build Runtime Image
```bash
docker build -t kalium:runtime --target runtime .
```

### Build Development Image
```bash
docker build -t kalium:dev --target development .
```

### Build with Custom Options
```bash
# Build without cache
docker build --no-cache -t kalium:latest .

# Build with specific platform
docker build --platform linux/amd64 -t kalium:amd64 .
docker build --platform linux/arm64 -t kalium:arm64 .
```

## Running the CLI Application

### Basic Usage

```bash
# Show help
docker run --rm kalium:runtime

# Login and listen to a group
docker run --rm -it kalium:runtime \
    java -Djava.library.path=/app/native/libs -jar /app/cli.jar \
    login --email user@example.com --password yourpassword listen-group
```

### With Persistent Data

```bash
# Create a volume for persistent data
docker volume create kalium-data

# Run with volume mounted
docker run --rm -it \
    -v kalium-data:/app/data \
    kalium:runtime \
    java -Djava.library.path=/app/native/libs -jar /app/cli.jar [command]
```

### With Environment Variables

```bash
# Pass environment variables
docker run --rm -it \
    -e KALIUM_LOG_LEVEL=DEBUG \
    -e KALIUM_API_URL=https://api.wire.com \
    kalium:runtime \
    java -Djava.library.path=/app/native/libs -jar /app/cli.jar [command]
```

## Development Workflow

### 1. Start Development Container

```bash
docker-compose run --rm kalium-dev /bin/bash
```

### 2. Inside Container

```bash
# Build the project
./gradlew clean build

# Run JVM tests
./gradlew jvmTest -Djava.library.path=./native/libs

# Run Android unit tests
./gradlew testDebugUnitTest

# Run linting
./gradlew detekt

# Build CLI
./gradlew :sample:cli:assemble

# Run CLI
java -jar sample/cli/build/libs/cli.jar --help
```

### 3. Code Changes

Since the development container mounts the source code as a volume, any changes you make on your host machine will be reflected immediately in the container.

## Database Setup (For Backend Implementation)

If you're implementing the sync outbox backend:

### 1. Start PostgreSQL

```bash
docker-compose up -d postgres
```

### 2. Create Schema

```bash
docker-compose exec postgres psql -U kalium -d kalium_sync -f - <<EOF
-- Create sync operations table
CREATE TABLE sync_operations (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    batch_id VARCHAR(255) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    sequence_id BIGINT NOT NULL,
    table_name VARCHAR(50) NOT NULL,
    operation_type VARCHAR(10) NOT NULL,
    row_key JSONB NOT NULL,
    row_data JSONB,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, sequence_id)
);

-- Create indexes
CREATE INDEX idx_sync_ops_user ON sync_operations(user_id);
CREATE INDEX idx_sync_ops_batch ON sync_operations(batch_id);
CREATE INDEX idx_sync_ops_timestamp ON sync_operations(timestamp);

-- Grant permissions
GRANT ALL PRIVILEGES ON TABLE sync_operations TO kalium;
GRANT USAGE, SELECT ON SEQUENCE sync_operations_id_seq TO kalium;
EOF
```

### 3. Connect Application to Database

```bash
# Set database connection in your backend application
DATABASE_URL=postgresql://kalium:kalium_secret@postgres:5432/kalium_sync
```

## Troubleshooting

### Build Fails

```bash
# Clear Docker build cache
docker builder prune -a

# Remove old images
docker rmi kalium:latest kalium:dev

# Rebuild from scratch
docker-compose build --no-cache
```

### Out of Memory

```bash
# Increase Docker memory limit in Docker Desktop settings
# Or set memory limit in docker-compose.yml:

services:
  kalium-dev:
    mem_limit: 4g
    memswap_limit: 4g
```

### Native Libraries Not Found

If you see errors about native libraries:

```bash
# Ensure native libraries are built
docker-compose run --rm kalium-dev make

# Verify library path
docker-compose run --rm kalium-runtime ls -la /app/native/libs
```

### Permission Issues

```bash
# Fix file ownership (if running as root)
docker-compose run --rm kalium-dev chown -R $(id -u):$(id -g) /app/build

# Or run with your user ID
docker-compose run --rm --user $(id -u):$(id -g) kalium-dev ./gradlew build
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build Docker Image

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  docker:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v2

      - name: Build runtime image
        run: docker build --target runtime -t kalium:runtime .

      - name: Build dev image
        run: docker build --target development -t kalium:dev .

      - name: Run tests in container
        run: |
          docker run --rm kalium:dev \
            ./gradlew jvmTest -Djava.library.path=./native/libs
```

## Production Deployment

### Using Docker Swarm

```bash
# Initialize swarm
docker swarm init

# Deploy stack
docker stack deploy -c docker-compose.yml kalium

# Scale services
docker service scale kalium_kalium-cli=3

# Update service
docker service update --image kalium:new-version kalium_kalium-cli
```

### Using Kubernetes

```yaml
# kalium-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kalium-cli
spec:
  replicas: 3
  selector:
    matchLabels:
      app: kalium-cli
  template:
    metadata:
      labels:
        app: kalium-cli
    spec:
      containers:
      - name: kalium
        image: kalium:runtime
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        volumeMounts:
        - name: data
          mountPath: /app/data
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: kalium-data-pvc
```

## Security Best Practices

1. **Use Multi-Stage Builds**: Already implemented to minimize final image size
2. **Run as Non-Root**: Runtime container runs as user `kalium` (UID 1000)
3. **Scan Images**: Use `docker scan kalium:latest` to check for vulnerabilities
4. **Pin Base Image Versions**: Update base images regularly
5. **Secrets Management**: Use Docker secrets or environment variables, never hardcode
6. **Network Isolation**: Use Docker networks to isolate services

## Optimization Tips

### Reduce Build Time

```bash
# Use BuildKit for faster builds
DOCKER_BUILDKIT=1 docker build -t kalium:latest .

# Use build cache
docker build --cache-from kalium:latest -t kalium:latest .
```

### Reduce Image Size

The multi-stage build already minimizes size:
- Builder stage: ~2GB (full JDK + build tools)
- Runtime stage: ~300MB (JRE only)

### Parallel Builds

```bash
# Build multiple stages in parallel
docker-compose build --parallel
```

## Monitoring and Logging

### View Logs

```bash
# Follow logs
docker-compose logs -f kalium-cli

# View last 100 lines
docker-compose logs --tail=100 kalium-cli

# Export logs
docker-compose logs kalium-cli > kalium.log
```

### Health Checks

```bash
# Check container health
docker-compose ps

# Inspect container
docker inspect kalium-cli

# View resource usage
docker stats kalium-cli
```

## Cleanup

```bash
# Stop all services
docker-compose down

# Remove volumes
docker-compose down -v

# Remove images
docker-compose down --rmi all

# Full cleanup (including volumes)
docker-compose down -v --rmi all --remove-orphans

# Prune unused Docker resources
docker system prune -a --volumes
```

---

## Additional Resources

- [Kalium Documentation](./CLAUDE.md)
- [Sync Outbox Implementation](./SYNC_OUTBOX_IMPLEMENTATION.md)
- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)

---

**Note**: This deployment is designed for the Kalium SDK and CLI. For production deployment of a complete Wire backend system, additional components and configuration will be required.
