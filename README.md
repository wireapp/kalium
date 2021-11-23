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

Note the the path needs to be adjusted for your machine.

#### Running the tests

In `build.gradle.kts` your test task should look something like this:

```kotlin
tasks.test {
    useJUnitPlatform()
    jvmArgs = jvmArgs?.plus(listOf("-Djava.library.path=/usr/local/lib/:/Users/tmpz/Code/Wire/kalium/native/libs"))
}
```

The path is the same as the one you have passed to the VM options, adjusted for your machine.
