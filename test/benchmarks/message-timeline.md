# Message timeline benchmarks

These benchmarks cover the keyset timeline API from
[ADR 14](../../docs/adr/0014-keyset-pagination-for-message-timelines.md).

The shared fixture runs through JVM file-backed SQLite and Android's existing encrypted SQLCipher
database builder. Each trial seeds its own database, validates the target page, opens a stable
generation, and obtains continuation cursors before measurement. Teardown closes both timeline
sessions, invalidates the old Paging source, and closes and deletes the database.

| Operation | Measured work |
| --- | --- |
| `offsetFullEntities` | Existing Paging load, including its total count, full message view mapping, and per-message multipart reads |
| `keysetPageEntities` | New cursor query and batched page assembly in persistence |
| `keysetListItems` | The same new path plus mapping to the public `MessageListItem` contract |
| `newerListItems` | Nearest-newer selection, reversed into display order, with the same target IDs and list mapping |
| `aroundMessage` | Message-ID lookup, both cursor directions, batched assembly, and public list mapping |

All operations include coroutine dispatch and consume the complete returned object. They retain
only the measured page. The old baseline returns persistence entities and does not include the
Android app's UI mapping or extra member lookups. Compare `keysetPageEntities` with `offsetFullEntities`
for persistence cost; `keysetListItems` adds the new shared rendering model. `aroundMessage` is an
anchor-navigation measurement with a different page window.

## Fixtures

`position` is `conversation size:stored rows before the target`. It covers 10,000 messages at depths
0, 1,000, and 9,000, and 100,000 messages at depths 0, 1,000, 10,000, and 90,000. Both page sizes 20
and 60 are measured. Positions near the end still have a complete page.

| Profile | Data |
| --- | --- |
| `TEXT` | Text, a mention, link preview, and a quote of an older message |
| `MIXED` | Text, larger text, image assets, unknown payloads, multipart attachments, reactions, receipts, and member events |
| `MULTIPART` | Four distinct cell attachments per message |
| `REACTIONS` | Eight reacting users, two emoji each, and read receipts per message |
| `MEMBERS` | 120 members per system event, repeated across the page to exercise user deduplication |
| `SPARSE` | Runs of 900 hidden rows between blocks of 100 eligible messages |
| `TIES` | Groups of 128 messages sharing an exact stored timestamp |

The sparse baseline translates the stored-row position to the matching visible-row offset outside
measurement. Ties validate exact IDs for the new API and page size for the old API, whose ID order
within a timestamp is unspecified. Other profiles require identical target IDs in both paths.
The full matrix is large; start with one profile and position on the intended device.

## Build without running

```shell
./gradlew :test:benchmarks:mainBenchmarkCompile :test:android-benchmarks:compileReleaseAndroidTestKotlin
```

The default JVM benchmark configuration excludes this suite. Its dedicated configuration requires
`-PenableMessageTimelineBenchmarks=true`. Android cases require the `runMessageTimelineBenchmarks`
instrumentation argument before seeding or measuring anything.

## Run

JVM sample-time measurements report latency distributions, including p50 and p95. Run the dedicated
configuration explicitly:

```shell
./gradlew :test:benchmarks:mainMessageTimelineBenchmark -PenableMessageTimelineBenchmarks=true
```

For a smaller JVM selection, build the JMH jar and pass JMH parameters to it directly:

```shell
./gradlew :test:benchmarks:mainBenchmarkJar
java -jar test/benchmarks/build/benchmarks/main/jars/benchmarks-main-jmh-*-JMH.jar MessageTimelineBenchmark \
  -p position=100000:10000 -p profile=MIXED -p pageSize=20
```

Add `-prof gc` to report allocation per operation. Keep the full JMH JSON output (`-rf json`) and
record the JDK, machine, revision, and database settings.

On Android, choose a release device and select this class explicitly. This example selects one case
for all five measured operations:

```shell
./gradlew :test:android-benchmarks:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.wire.kalium.benchmarks.android.MessageTimelineBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.runMessageTimelineBenchmarks=true \
  -Pandroid.testInstrumentationRunnerArguments.timelinePosition=100000:10000 \
  -Pandroid.testInstrumentationRunnerArguments.timelineProfile=MIXED \
  -Pandroid.testInstrumentationRunnerArguments.timelinePageSize=20
```

Keep the Android benchmark JSON and record device, OS, SQLCipher version, revision, and thermal state.
The fixture runs warm reads after setup validation. Cold starts, retained heap, index/write cost,
an isolated keyset/full-view control, the iOS production driver, and client scrolling/frame timing
remain separate measurements listed in the ADR's rollout gates. Database correctness tests already assert
the indexed cursor plan and batched child queries; these measurements do not replace them.
