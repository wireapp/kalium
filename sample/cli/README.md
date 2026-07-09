# Kalium CLI

Command-line application for testing and debugging Kalium flows.

The CLI can:

- Log in
- Create a group conversation
- Add and remove group members
- Receive and send text messages in real time
- Remove another client from your account remotely
- Refill MLS key packages
- Mark conversations as read
- Update supported protocols

## Requirements

- JDK 21
- Native libraries under `native/libs`

Build native libraries from the repository root:

```bash
make
```

When running JVM tasks, pass the native library path:

```bash
-Djava.library.path=./native/libs
```

## Build

```bash
./gradlew :sample:cli:jvmJar -Djava.library.path=./native/libs
```

The fat jar is written under:

```text
sample/cli/build/libs/
```

## Run On JVM

Use the jar produced in `sample/cli/build/libs/`:

```bash
java -Djava.library.path=./native/libs -jar sample/cli/build/libs/<cli-jar>.jar login --email <email> --password <password> listen-group
```

You can also use the Gradle run task:

```bash
./gradlew :sample:cli:jvmRun --console=plain --quiet --args="login --email <email> --password <password> listen-group"
```

## Run Native On macOS

```bash
./gradlew :sample:cli:macosArm64Binaries
./sample/cli/build/bin/macosArm64/debugExecutable/cli.kexe login
```

## Commands

Run help from the built CLI to see the current command list:

```bash
java -Djava.library.path=./native/libs -jar sample/cli/build/libs/<cli-jar>.jar login --help
```

Current command set:

```text
create-group
listen-group
delete-client
add-member
remove-member
console
refill-key-packages
mark-as-read
update-supported-protocols
```
