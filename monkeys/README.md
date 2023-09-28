# Infinite Monkeys

This application enables the creation of stress tests in clients and in the backend.

## Building

To build, run:

```bash
./gradlew :monkeys:build
```

A jar file will be created inside `./monkeys/build/libs` called `monkeys.jar`.

### Docker

Optionally a docker image can be built to run the app in containers. Inside the `monkeys/docker`
folder, run:

```bash
docker compose up
```

By default, it will search for a config named `config.json` inside the `monkeys/docker/config` folder. To change
which
config file to load simply set the environment variable `CONFIG`:

```bash
env CONFIG=example.json docker compose up
```

*Note*: the file must be located inside the config folder.

If the host is not an ARM based platform, the `--platform` parameter can be omitted. If building on
MacOs ARM computers, be sure to disable Rosetta emulation and containerd support in the settings
under `Features in development` from docker.

Under this stack, prometheus and grafana containers will be started, and they'll automatically
scrape metrics from the monkeys application to visualize graphs. Grafana should be available on the
port [3000](http://localhost:3000/) and prometheus on the port [9090](http://localhost:9090)

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

When creating teams and users automatically through Infinite Monkeys, remember to set the `authUser`
and `authPassword` with the credentials for the internal API of the respective backend.

## Current Limitations (to be fixed in the future)

* The `SIGINT` signal is not being correctly processed by the app.
* The application should run until it receives a `SIGINT` (Ctrl+C) signal. There should be a
  configuration to finish the test run
* Tests need to be implemented
* Collecting metrics about the test execution
* Tracing and replaying a test run. For this the order is the important factor, so when replayed it
  won't be executed in parallel.
* Multi-clients. Right now each user can have just one client within the application
* Clean-up the data created during the tests
* Randomising times for action execution
