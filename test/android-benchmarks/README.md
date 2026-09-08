# Android persistence benchmarks

These benchmarks use the production SQLCipher database builder and AndroidX Benchmark on a physical
Android device. The refactored suite has not been run. Use consistent device and build conditions
when comparing results. JVM timings from [the other runner](../benchmarks/README.md) are a separate baseline.

| Benchmark | Measured operation |
| --- | --- |
| `MessageInsertBenchmark` | Insert 5,000 messages into a database containing 20,000 |
| `MessagePagingBenchmark` | Load 50 messages through a fresh Pager at offset 0 or 5,000 |
| `MarkReadBenchmark` | Mark one incoming message read, with 20,000 existing messages |
| `AssetStatusBenchmark` | Read 1,000 asset statuses among 20,000 messages |
| `ConcurrentMessageBenchmark` | Insert 5,000 messages individually while another coroutine repeatedly reads 50 rows |
| `ConversationListBenchmark` | Compare legacy and V2 initial/restore loads of 20 rows |
| `ConversationListWriteBenchmark` | Insert one message and update conversation recency, with an optional index |

Message benchmarks share a fixed dataset with the JVM runner. Each write benchmark removes its
inserted messages and restores conversation dates outside measurement. Databases are closed and
deleted after each case. The concurrent case measures completion of the whole workload, including
reader startup and teardown; it does not report individual read latency.

## Conversation list comparison

The 36 cases cover legacy and V2, each at first, middle, and deep positions for All, Groups, Channels,
One-to-one, Archive, and contains-search. Both implementations must return the same IDs. Middle and
deep positions are relative to each filtered source. They measure restored viewports using fresh
Pagers, including presentation and teardown, rather than scrolling through append/prepend loads.

| Instrumentation argument | Default | Meaning |
| --- | --- | --- |
| `conversationListCount` | `5000` | Conversation count, minimum 1,000 |
| `conversationListHistory` | `20` | Messages per conversation, minimum 5 |
| `conversationListTypeIndex` | `false` | Add the candidate type/recency index to the fixture |

The fixture has 85% groups, 10% one-to-one conversations, and 5% channels. Archived groups make up
10% of all conversations. Every third conversation has five synthetic unread events, every seventh
matches search, and every seventeenth has a draft. Setup runs `ANALYZE`. Both index modes retain the
production global recency index. The candidate index is confined to the fixture.

## Compile and run

Compile without running or installing benchmarks:

```sh
./gradlew :test:android-benchmarks:compileReleaseAndroidTestKotlin
```

Run the full suite:

```sh
./gradlew :test:android-benchmarks:connectedReleaseAndroidTest
```

Run only the list comparison and its write benchmark:

```sh
./gradlew :test:android-benchmarks:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.wire.kalium.benchmarks.android.ConversationListBenchmark,com.wire.kalium.benchmarks.android.ConversationListWriteBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.conversationListCount=5000 \
  -Pandroid.testInstrumentationRunnerArguments.conversationListHistory=20 \
  -Pandroid.testInstrumentationRunnerArguments.conversationListTypeIndex=false
```

Repeat with `conversationListTypeIndex=true` for the index comparison. Use 1,000 conversations and
five messages for an initial wiring check. `-PsqlcipherVersion=...` selects a different SQLCipher
version when testing an upgrade.

Save AndroidX JSON from this module's `build/outputs`, the commit, device information, and fixture
metadata from logcat in a separate directory for each run. Keep index modes and datasets separate.
Compare repeated runs against [ADR 0011](../../docs/adr/0011-keyset-paginated-conversation-list.md),
reporting search separately. Alternate comparison order when confirming a result.

AndroidX timing samples can average multiple iterations. A p95 calculated from `metrics.timeNs.runs`
is a percentile of those samples, not individual message-write latencies. Keep the raw samples and
record the calculation. Start new baselines for the rewritten message benchmarks.
