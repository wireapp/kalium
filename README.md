# kalium
Kalium

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

If you get `Unknown host CPU architecture: arm64` on Apple Silicon (M1 Mac), [follow this stack overflow answer](https://stackoverflow.com/questions/69541831/unknown-host-cpu-architecture-arm64-android-ndk-siliconm1-apple-macbook-pro).

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
