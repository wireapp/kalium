FROM eclipse-temurin:17 AS jdk-build

COPY .. /kalium
RUN cd /kalium && ./gradlew :monkeys:assembleDist
RUN tar -xf /kalium/monkeys/build/distributions/monkeys.tar

FROM wirebot/cryptobox:1.4.0

# install docker cli
RUN install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc && \
    chmod a+r /etc/apt/keyrings/docker.asc && \
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
      $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
      tee /etc/apt/sources.list.d/docker.list > /dev/null
RUN apt-get update && apt-get install -y docker-ce-cli

RUN mkdir /opt/app
COPY --from=jdk-build /monkeys /opt/app
