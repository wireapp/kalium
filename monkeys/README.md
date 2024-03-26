# Infinite Monkeys

This application enables the creation of stress tests in clients and in the backend.

## Building

To build, run:

```bash
./gradlew :monkeys:assembleDist
```

A zip and tar files will be created inside `./monkeys/build/distributions`. In the uncompressed
folder, you'll see a folder called bin which contains 3 scripts to start the monkeys, the replayer
and the monkey-server.

### Docker

Optionally a docker image can be built to run the app in containers. Inside the `monkeys/docker`
folder, run:

```bash
docker compose up
```

By default, it will search for a config named `config.json` inside the `monkeys/docker/config`
folder. To change
which
config file to load simply set the environment variable `CONFIG`:

```bash
env CONFIG=example.json docker compose up
```

_Note_: the file must be located inside the config folder.

If the host is not an ARM based platform, the `--platform` parameter can be omitted. If building on
MacOs ARM computers, be sure to disable Rosetta emulation and containerd support in the settings
under `Features in development` from docker.

Under this stack, prometheus and grafana containers will be started, and they'll automatically
scrape metrics from the monkeys application to visualize graphs. Grafana should be available on the
port [3000](http://localhost:3000/) and prometheus on the port [9090](http://localhost:9090)

## Running

Create a configuration and execute:

```bash
./bin/monkeys config.json
```

For a list of the possible options and parameters run:

```bash
./bin/monkeys --help
```

An [example](example.json) config is in this repo and the schema can be seen [here](schema.json).

When creating teams and users automatically through Infinite Monkeys, remember to set the `authUser`
and `authPassword` with the credentials for the internal API of the respective backend.

### Running each monkey in detached mode

It is possible to split each monkey from the main application and that can improve performance and
scalability. To do that you need to specify a command to start each monkey and an address to resolve
them. Both the `startCommand` and the `addressTemplate` are templated string the following variables
will be replaced at runtime:

- teamName: the name of the team that the user belongs to
- email: the email of the user
- userId: the id of the user (without the domain)
- teamId: the id of the team that the user belongs to
- monkeyIndex: a unique identifier within the app for each monkey (it is numeric starting from 0)
- monkeyClientId: a unique identifier for each client within the app (it is numeric)
- code: the 2FA code that can be reused until expired.

Example:

```json
{
    "externalMonkey": {
        "startCommand": "./monkeys/bin/monkey-server -p 50{{monkeyIndex}}",
        "addressTemplate": "http://localhost:50{{monkeyIndex}}",
        "waitTime": 3
    }
}
```

It is also possible to run this inside docker. Here's an example (the `--platform` option is only
needed on non x86_64 CPUs):

```json
{
    "externalMonkey": {
        "startCommand": "docker run --platform linux/amd64 --network docker_monkeys --hostname monkey-{{monkeyIndex}} monkeys /opt/app/bin/monkey-server",
        "addressTemplate": "http://monkey-{{monkeyIndex}}:8080",
        "waitTime": 3
    }
}
```

Or on swarm mode:

```json
{
    "externalMonkey": {
        "startCommand": "docker service create --hostname monkey-{{monkeyIndex}} --name monkey-{{monkeyIndex}}-{{teamName}} --network infinite-monkeys_monkeys --restart-condition none monkeys /opt/app/bin/monkey-server",
        "addressTemplate": "http://monkey-{{monkeyIndex}}:8080",
        "waitTime": 3
    }
}
```

The `waitTime` field is optional and determines how long (in seconds) it will wait until it proceeds to start the
next monkey. This can be important because the next step in the app is assigning each to a user and
the app must be ready to respond.

**Note**: setting environment variables prior to the command is is not supported. Ex:
`INDEX={{monkeyIndex}} ./monkeys/bin/monkey-server -p 50$INDEX`. This is a limitation of the JVM's system command
runner.

## Current Limitations (to be fixed in the future)

- The application should run until it receives a `SIGINT` (Ctrl+C) signal. There should be a
    configuration to finish the test run
- Tests need to be implemented
- Randomising times for action execution
