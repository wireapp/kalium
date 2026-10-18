# JVM persistence benchmarks

JMH measures the persistence APIs against a file-backed, unencrypted SQLite database.
Use [the Android suite](../android-benchmarks/README.md) for device measurements with SQLCipher.
JVM and Android timings are separate baselines; their difference does not isolate encryption cost.

| Benchmark | Measured operation | Data |
| --- | --- | --- |
| `MessageInsertBenchmark` | Insert a fixed batch of 5,000 messages | 20,000 existing messages |
| `MessagePagingBenchmark` | Load 50 messages through a fresh Pager at offset 0 or 5,000 | 20,000 messages |
| `ConversationPagingBenchmark` | Load 50 legacy conversation rows through a fresh Pager at offset 0 or 500 | 2,000 groups, 20 messages each |
| `MarkReadBenchmark` | Mark one incoming message read | 20,000 existing messages |

The message fixture and page collector live in `test/benchmark-fixtures/kotlin` and are compiled
into both runners. Messages use a fixed text, asset, knock, and unknown-content mix. Setup, result
checks, and resetting writes happen outside measurement. Paging includes coroutine setup,
presentation, and collection teardown. Cancelled paging sources are invalidated outside measurement
to detach their database listeners. These are warm database initial/restore loads.

The [message timeline suite](message-timeline.md) compares the existing offset path with keyset page
assembly and list mapping on JVM and Android. It is opt-in.

Compile the sources and generated JMH runner without running benchmarks:

```sh
./gradlew :test:benchmarks:mainBenchmarkCompile
```

Run the suite, or select a benchmark class:

```sh
./gradlew :test:benchmarks:benchmark
./gradlew :test:benchmarks:benchmark -PbenchmarkFilter=MessagePagingBenchmark
```

Timing settings live in `build.gradle.kts`: three warmup iterations and ten measurement iterations,
one second each, reporting average milliseconds per operation. Raw JSON reports are under
`test/benchmarks/build/reports/benchmarks`. Keep the report with the commit, JVM version, and machine
information. The manual CI workflow uploads the raw reports; it does not enforce performance gates.
