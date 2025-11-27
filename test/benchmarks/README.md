# Kalium benchmarks

The module consists of benchmarks aimed to track performance of different isolated layers
of `Kalium` like; `persistence`, `crypto`, `network`, etc.

Currently, the suite includes benchmarks on:

- [persistence module](../persistence)

The rest of the benchmarks, to other layers will be added later.

### Running benchmarks

To run the benchmarks you can use the following command:

```shell
./gradlew clean benchmark
```
