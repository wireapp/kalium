FROM eclipse-temurin:17-jdk

# disable prompts from the txdata
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    build-essential \
    cargo \
    gcc \
    software-properties-common \
    unzip \
    clang \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . .

RUN ./gradlew :tools:testservice:shadowJar

FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY --from=0 /app/tools/testservice/build/libs/ /app/testservice/
COPY --from=0 /app/tools/testservice/config.yml /app/testservice/config.yml

RUN useradd -m appuser
RUN chown -R appuser:appuser /app

CMD ["java", "-jar", "/app/testservice/testservice-0.0.1-SNAPSHOT-all.jar", "server", "/app/testservice/config.yml"]