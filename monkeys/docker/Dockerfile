FROM eclipse-temurin:17 AS jdk-build

COPY .. /kalium
RUN cd /kalium && ./gradlew :monkeys:assembleDist
RUN tar -xf /kalium/monkeys/build/distributions/monkeys.tar

FROM wirebot/cryptobox:1.4.0

RUN mkdir /opt/app
COPY --from=jdk-build /monkeys /opt/app
