
<img src="/.idea/icon.png" alt="Kalium Logo" style="max-width:100%;">

# Kalium
[![JVM & JS Tests](https://github.com/wireapp/kalium/actions/workflows/gradle-jvm-tests.yml/badge.svg)](https://github.com/wireapp/kalium/actions/workflows/gradle-jvm-tests.yml)
[![codecov](https://codecov.io/gh/wireapp/kalium/branch/develop/graph/badge.svg?token=UWQ1P7DY7I)](https://codecov.io/gh/wireapp/kalium)

## How to build

### Dependencies

- JDK 21 (ex: openjdk-21-jdk on Ubuntu)
- Git (required for build process)

### Supported Platforms

- Android (see the [Android](https://github.com/wireapp/wire-android) module)
- JVM (see the [cli](https://github.com/wireapp/kalium/tree/develop/cli) module)
- iOS (see the [iOS Build Guide](docs/IOS_BUILD.md))
- JavaScript (just a tiny bit)

### Compile-time flags

Kalium currently uses the following compile-time Gradle properties:

- `USE_UNIFIED_CORE_CRYPTO`
  - Default: `false` (see `gradle.properties`)
  - Controls whether Kalium uses the unified `core-crypto-kmp` artifact (`true`) or platform-specific crypto artifacts (`false`).
  - Override example:
    ```bash
    ./gradlew <task> -PUSE_UNIFIED_CORE_CRYPTO=true
    ```

- `SHARE_USER_STORAGE_CACHE_BETWEEN_PROVIDERS`
  - Default for standalone Kalium builds: `false` (per-`CoreLogic` cache, backwards-compatible behavior).
  - Included-build behavior (when Kalium is built from Wire Android): defaults to `true` unless explicitly overridden with `-PSHARE_USER_STORAGE_CACHE_BETWEEN_PROVIDERS=...`.
  - `true`: share user DB storage cache across `CoreLogic` instances in the same process.
  - `false`: cache is scoped to each `CoreLogic` instance.
  - Override example:
    ```bash
    ./gradlew <task> -PSHARE_USER_STORAGE_CACHE_BETWEEN_PROVIDERS=true
    ```
- `USE_GLOBAL_USER_NETWORK_API_CACHE`
    - Default for standalone Kalium builds: `false` (cache scoped to each `UserAuthenticatedNetworkProvider` instance).
    - Included-build behavior (when Kalium is built from Wire Android): defaults to `true` unless explicitly overridden with `-PUSE_GLOBAL_USER_NETWORK_API_CACHE=...`.
    - `true`: share authenticated network API containers process-wide across provider instances.
    - `false`: cache is local to each provider instance.
    - Override example:
      ```bash
      ./gradlew <task> -PUSE_GLOBAL_USER_NETWORK_API_CACHE=true
      ```

The `cli` can be executed on the terminal of any machine that 
satisfies the dependencies mentioned above, and is capable of actions like:
- Logging in
- Create a group conversation
- Add user to group conversation
- Receive and send text messages in real time
- Remove another client from your account remotely
- Refill MSL key packages

#### Building dependencies on macOS 12

Just run `make`, which will download and compile dependencies listed above from source, 
the output will be `$PROJECT_ROOT$/native/libs`

#### Running on your machine

When running any tasks that require the native libraries (`libsodium`, `cryptobox-c` 
and `cryptobox4j`), you need to pass their location as VM options like so:

```
-Djava.library.path=./path/to/native/libraries/mentioned/before
```

For example, if you want to run the task `jvmTest` and the libraries are in `./native/libs`:

```
./gradlew jvmTest -Djava.library.path=./native/libs
```

#### Running the CLI

You can see all commands and options by running `login --help`

```
Usage: cliapplication login [OPTIONS] COMMAND [ARGS]...

Options:
  -e, --email TEXT     Account email
  -p, --password TEXT  Account password
  --environment TEXT   Choose backend environment: can be production, staging
                       or an URL to a server configuration
  -h, --help           Show this message and exit

Commands:
  create-group
  listen-group
  delete-client
  add-member
  remove-member
  console
  refill-key-packages
  mark-as-read                Mark a conversation as read
  update-supported-protocols
```

##### JVM

```
./gradlew :sample:cli:assemble
java -jar cli/build/libs/cli.jar login --email <email> --password <password> listen-group 
```

or if you want the jar file deleted after your run:

```
./gradlew :sample:cli:run  --console=plain --quiet --args="login --email <email> --password <password> listen-group"
```

##### Native (Mac)

For running on arm64 mac
```
./gradlew :sample:cli:macosArm64Binaries
./cli/build/bin/macosArm64/debugExecutable/cli.kexe login
```

#### Detekt rules

We use and try to maintain our codestyle uniformed, so apart from having our checks in place in our
CI. You can have live feedback using the IDE, here is how:

1. IntelliJ -> Settings -> Plugins -> Marketplace -> Search and install "Detekt"
2. Settings -> Tools -> Detekt -> set: (replace $PROJECT_ROOT accordingly to your machine)

    - Configuration Files: $PROJECT_ROOT/detekt/detekt.yml
    - Baseline File: $PROJECT_ROOT/detekt/baseline.yml (optional)
    - Plugin Jars: $PROJECT_ROOT/detekt-rules/build/libs/detekt-rules.jar (this will add our custom
      rules to provide live feedback)

or

You can run locally in your terminal:

```
./gradlew clean detekt
```

#### Dependency Graph

```mermaid
%%{
  init: {
    'theme': 'neutral'
  }
}%%

graph LR
  :logic["logic"]
  subgraph :core
    :core:cryptography["cryptography"]
    :core:logger["logger"]
    :core:util["util"]
    :core:common["common"]
    :core:data["data"]
    :core:cryptography["cryptography"]
    :core:common["common"]
    :core:data["data"]
    :core:logger["logger"]
    :core:cryptography["cryptography"]
    :core:util["util"]
    :core:data["data"]
    :core:common["common"]
  end
  subgraph :domain
    :domain:backup["backup"]
    :domain:calling["calling"]
    :domain:cells["cells"]
    :domain:backup["backup"]
    :domain:cells["cells"]
    subgraph :messaging
      :domain:messaging:sending["sending"]
      :domain:messaging:sending["sending"]
    end
  end
  subgraph :data
    :data:protobuf["protobuf"]
    :data:network["network"]
    :data:network-model["network-model"]
    :data:network-util["network-util"]
    :data:persistence["persistence"]
    :data:network["network"]
    :data:persistence["persistence"]
    :data:persistence-test["persistence-test"]
    :data:network-util["network-util"]
    :data:network["network"]
    :data:data-mappers["data-mappers"]
    :data:persistence["persistence"]
    :data:protobuf["protobuf"]
    :data:persistence-test["persistence-test"]
    :data:network-model["network-model"]
    :data:network-util["network-util"]
    :data:data-mappers["data-mappers"]
  end
  subgraph :test
    :test:mocks["mocks"]
    :test:data-mocks["data-mocks"]
    :test:data-mocks["data-mocks"]
    :test:mocks["mocks"]
  end

  :core:cryptography --> :core:logger
  :domain:backup --> :data:protobuf
  :data:network --> :data:network-model
  :data:network --> :core:logger
  :data:network --> :data:protobuf
  :data:network --> :core:util
  :data:network --> :data:network-util
  :data:network --> :test:mocks
  :core:common --> :core:data
  :core:common --> :core:logger
  :core:common --> :core:util
  :core:common --> :data:persistence
  :core:common --> :data:network
  :core:common --> :data:network-util
  :core:common --> :core:cryptography
  :data:persistence --> :core:logger
  :data:persistence --> :core:util
  :data:persistence-test --> :data:persistence
  :logic --> :core:common
  :logic --> :core:data
  :logic --> :data:network-util
  :logic --> :core:logger
  :logic --> :domain:calling
  :logic --> :data:network
  :logic --> :data:data-mappers
  :logic --> :core:cryptography
  :logic --> :data:persistence
  :logic --> :data:protobuf
  :logic --> :core:util
  :logic --> :domain:cells
  :logic --> :domain:backup
  :logic --> :domain:messaging:sending
  :logic --> :data:persistence-test
  :logic --> :test:data-mocks
  :data:network-model --> :core:logger
  :data:network-model --> :core:util
  :test:data-mocks --> :core:data
  :test:data-mocks --> :data:persistence
  :test:data-mocks --> :data:network-model
  :test:data-mocks --> :core:util
  :data:network-util --> :core:logger
  :core:data --> :data:network-model
  :core:data --> :core:util
  :test:mocks --> :data:network-model
  :domain:messaging:sending --> :core:common
  :domain:messaging:sending --> :core:data
  :domain:messaging:sending --> :core:logger
  :domain:messaging:sending --> :core:util
  :domain:cells --> :core:common
  :domain:cells --> :data:network
  :domain:cells --> :core:data
  :domain:cells --> :core:util
  :domain:cells --> :data:persistence
  :data:data-mappers --> :core:data
  :data:data-mappers --> :data:protobuf
  :data:data-mappers --> :data:persistence
  :data:data-mappers --> :core:cryptography
  :data:data-mappers --> :data:network-model
  :data:data-mappers --> :core:util

classDef kotlin-multiplatform fill:#C792EA,stroke:#fff,stroke-width:2px,color:#fff;
class :core:cryptography kotlin-multiplatform
class :core:logger kotlin-multiplatform
class :domain:backup kotlin-multiplatform
class :data:protobuf kotlin-multiplatform
class :data:network kotlin-multiplatform
class :data:network-model kotlin-multiplatform
class :core:util kotlin-multiplatform
class :data:network-util kotlin-multiplatform
class :test:mocks kotlin-multiplatform
class :core:common kotlin-multiplatform
class :core:data kotlin-multiplatform
class :data:persistence kotlin-multiplatform
class :data:persistence-test kotlin-multiplatform
class :logic kotlin-multiplatform
class :domain:calling kotlin-multiplatform
class :data:data-mappers kotlin-multiplatform
class :domain:cells kotlin-multiplatform
class :domain:messaging:sending kotlin-multiplatform
class :test:data-mocks kotlin-multiplatform

```
#### Logo

The logo is adapted from [OpenMoji](https://openmoji.org/) – the open-source emoji and icon project. License: [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)
