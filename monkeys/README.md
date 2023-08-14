# Infinite Monkeys

This application enables the creation of stress tests in clients and in the backend.

To use, simply create a configuration and execute:

```bash
./gradlew :monkeys:run --args="config.json"
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
