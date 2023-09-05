# Infinite Monkeys

This application enables the creation of stress tests in clients and in the backend.

## Building

To build, run:

```bash
./gradlew :monkeys:build
```

A jar file will be created inside `./monkeys/build/libs` called `monkeys.jar`.

## Running

Create a configuration and execute:

```bash
java -jar monkeys.jar config.json
```

For a list of the possible options and parameters run:

```bash
java -jar monkeys.jar --help
```

An [example](example.json) config is in this repo and the schema can be seen [here](schema.json).

## Current Limitations (to be fixed in the future)

* The application runs until it receives a `SIGINT` (Ctrl+C) signal. There should be a configuration
  to finish the test run
* Tests need to be implemented
* Collecting metrics about the test execution
* Tracing and replaying a test run. For this the order is the important factor, so when replayed it
  won't be executed in parallel.
* Multi-clients. Right now each user can have just one client within the application
* Clean-up the data created during the tests
* Randomising times for action execution
