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

RUN make
RUN ./gradlew clean
RUN ./gradlew :testservice:shadowJar
