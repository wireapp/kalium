# 8. androidx.benchmark for on-device DAO benchmarks

Date: 2026-04-23

## Status

Proposed

## Context

Kalium currently has two complementary benchmark surfaces for the persistence layer and they leave
a gap that matters in practice:

1. `:test:benchmarks` — JMH-based JVM benchmarks (`MessagesNoPragmaTuneBenchmark`,
   `MessageReadBenchmark`). These run on the JVM with `userDatabaseBuilder(..., passphrase = null, ...)`,
   i.e. plain SQLite. They give us a JVM baseline but they do not exercise the SQLCipher code path
   that production Android clients actually hit.

2. `:data:persistence` `MessageDAOBenchmarkTest` (in `commonTest`, `@Ignore`d). On Android it does
   go through the encrypted path (`BaseDatabaseTest.encryptedDBSecret` → `SupportOpenHelperFactory`
   → libsqlcipher), but it measures with `measureTime { ... }` and lives alongside correctness
   tests. There is no warmup, no iteration control, no output stability check, and no JSON report.
   It is effectively a developer-only affordance you have to un-ignore by hand.

The question we cannot answer today is: *what is the real cost of message insert/select on a device
with production-equivalent encryption enabled?* We have a regression-prone piece of code
(`MessageDAO` on SQLCipher) and no repeatable measurement for it.

## Decision

Introduce `androidx.benchmark` (microbenchmark, JUnit4 variant) as the measurement framework for
on-device DAO benchmarks, and add a dedicated module `:test:android-benchmarks` to host those
benchmarks. The first two scenarios ported are message insert and message query by conversation —
the ones already covered by `MessagesNoPragmaTuneBenchmark` on JVM — so we have a direct
JVM-plain vs Android-encrypted comparison.

Key shape:

- **New module `:test:android-benchmarks`**, a plain `com.android.library` module (not KMP). Our
  existing `kalium.library` convention plugin is KMP-Android, which isn't a great fit for
  `androidx.benchmark` today; keeping this module classic keeps the benchmark plugin happy and
  confines the non-standard build settings.
- **Release-only instrumented tests**: `testBuildType = "release"`, `debuggable = false`,
  `minifyEnabled = false`. This is a hard requirement of `androidx.benchmark` — debuggable builds
  are refused (or produce meaningless numbers).
- **Runner**: `androidx.benchmark.junit4.AndroidBenchmarkRunner` (replaces the default
  `AndroidJUnitRunner` for this module only).
- **Encryption path**: benchmarks build the user DB via `userDatabaseBuilder` with a non-null
  `UserDBSecret`, matching what `BaseDatabaseTest` does on Android today. libsqlcipher is loaded
  and `SupportOpenHelperFactory` drives the connection — this is the production path.
- **Storage**: file-backed DB under `context.cacheDir`; deleted per-class to keep runs clean.
- **Scenarios (first cut)**:
  - `MessageInsertBenchmark`: pre-seed 1 000 messages; measure `insertOrIgnoreMessages(5 000)`.
  - `MessageSelectBenchmark`: pre-seed 5 000 messages; measure
    `getMessagesByConversationAndVisibility(limit = 1 000).first()`.
- **Dependencies added** (to `gradle/libs.versions.toml`):
  - `androidx-benchmark = "1.5.0-alpha05"` (new version key; alpha is required because the 1.4.x
    line targets the old AGP `TestedExtension` DSL and is incompatible with AGP 9.0)
  - `androidx.benchmark:benchmark-junit4` library
  - `androidx.benchmark` Gradle plugin
  - `androidx.test.ext:junit` (adds `AndroidJUnit4` runner; not previously in the catalog)
- **Run command**:
  ```
  ./gradlew :test:android-benchmarks:connectedReleaseAndroidTest
  ```
  Results land in `build/outputs/connected_android_test_additional_output/.../benchmarkData.json`.
- **Out of scope for this ADR**: CI integration, MessageRead/paging scenarios, iOS/XCTest
  measurement, fold into `:test:benchmarks` as a KMP target. Any of those come as separate ADRs
  once this module proves its worth.

**Module dependency direction:** `:test:android-benchmarks` depends on `:data:persistence`. It is a
leaf test module — nothing depends on it. It does not violate the existing layering rules.

## Consequences

**Easier:**

- Repeatable on-device measurement of the real SQLCipher path for message insert/select, with
  proper warmup, iteration control, and JSON output.
- A direct JVM-plain (JMH) ↔ Android-encrypted (androidx.benchmark) comparison for the same two
  scenarios, which previously required reading two different test files and squinting.
- A home for future on-device DAO benchmarks that doesn't pollute `:data:persistence`'s
  instrumented test run (which is meant for correctness and should stay debug-build).

**More difficult:**

- Two benchmark frameworks in the repo (`org.jetbrains.kotlinx.benchmark` for JMH,
  `androidx.benchmark` for on-device). Developers need to know which one to use for which surface.
  Guidance: JVM-level → JMH; Android-device → androidx.benchmark.
- New module means new `build.gradle.kts` to maintain, and libsqlcipher/libsodium packaging rules
  have to be mirrored here.
- Requires a physical device or emulator with stable CPU governor to produce usable numbers; this
  is not something we'll run in default CI without more work.
- One more dependency to keep updated.