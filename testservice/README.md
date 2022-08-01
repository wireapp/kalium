# testservice

Provides a REST service for sending test messages

## How to run

In root folder execute:

```shell
./gradlew :testservice:run
```

Create fat jar for standalone execution:

```shell
./gradlew :testservice:jar
```

Run fat jar:

```shell
java -jar testservice/build/libs/testservice-*.jar server testservice/config.yml
```
