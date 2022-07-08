# kalium
[![JVM & JS Tests](https://github.com/wireapp/kalium/actions/workflows/gradle-jvm-tests.yml/badge.svg)](https://github.com/wireapp/kalium/actions/workflows/gradle-jvm-tests.yml)
[![codecov](https://codecov.io/gh/wireapp/kalium/branch/develop/graph/badge.svg?token=UWQ1P7DY7I)](https://codecov.io/gh/wireapp/kalium)

## How to build

### Dependencies

- [libsodium](https://github.com/jedisct1/libsodium)
- [cryptobox-c](https://github.com/wireapp/cryptobox-c)
- [cryptobox4j](https://github.com/wireapp/cryptobox4j)


#### Building on macOS 12

Run `make`, then pass the libraries in `native/libs` and the location of `libsodium` to the VM options like so:

```
-Djava.library.path=/usr/local/lib/:/Users/tmpz/Code/Wire/kalium/native/libs
```

Note that the path needs to be adjusted for your machine.

##### Troubleshooting

###### Unknown host CPU architecture: arm64
`This solution won't work! an alternative solution is to use OpenJDK, but that won't work either, so PLEASE DON'T WASTE MORE TIME on it till there is a permanent and working solution for M1 machines! Otherwise, please use x86-based machines to build and run the CLI app!`

If you get `Unknown host CPU architecture: arm64` on Apple Silicon (M1 Mac)
, [follow this stack overflow answer](https://stackoverflow.com/questions/69541831/unknown-host-cpu-architecture-arm64-android-ndk-siliconm1-apple-macbook-pro)
.

Change `/Users/<your-user>/Library/Android/sdk/ndk/<your-ndk-version-number>` to

```
#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
arch -x86_64 /bin/bash $DIR/build/ndk-build "$@"
```

#### Running the tests

In `build.gradle.kts` your test task should look something like this:

```kotlin
tasks.test {
    useJUnitPlatform()
    jvmArgs = jvmArgs?.plus(listOf("-Djava.library.path=/usr/local/lib/:/Users/tmpz/Code/Wire/kalium/native/libs"))
}
```

The path is the same as the one you have passed to the VM options, adjusted for your machine.

#### Running the CLI

With the native libs in the classpath (-Djava.library.path=/usr/local/lib/:./native/libs):

```
./gradlew :cli:run --args="login"
```

or

```
./gradlew assemble
java -jar cli/build/libs/cli.jar login
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
