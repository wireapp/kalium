# Infinite Monkeys

This application enables the creation of stress tests in clients and in the backend.

To use, simply create a configuration and execute:

```bash
./gradlew :monkeys:run --args="config.json"
```

An [example](example.json) config is in this repo and the schema can be seen [here](schema.json)

## Current Limitations (to be fixed in the future)

* It is assumed that all users under a backend belongs to a single team
* The user picker currently doesn't check for duplications, so a relatively big user base is
  required to reduce the risk of duplications. This doesn't break the app, but can cause the same
  user to execute the actions repeatedly or a group with lower number of participants as configured
