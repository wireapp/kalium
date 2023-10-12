FROM eclipse-temurin:17 AS jdk-build

COPY .. /kalium
RUN cd /kalium && ./gradlew :monkeys:build

FROM wirebot/cryptobox:1.4.0

RUN mkdir /opt/app
COPY --from=jdk-build /kalium/monkeys/build/libs/monkeys.jar /opt/app
ENTRYPOINT ["java", "-jar", "/opt/app/monkeys.jar"]
CMD ["/config.json"]
