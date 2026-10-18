# 8. androidx.benchmark for on-device DAO benchmarks

Date: 2026-04-23

## Status

Accepted

## Context

Kalium currently has two complementary benchmark surfaces for the persistence layer and they leave
a gap that matters in practice:

1. `:test:benchmarks` — JMH-based JVM benchmarks. These run on the JVM with
   `userDatabaseBuilder(..., passphrase = null, ...)`, i.e. plain SQLite. They give us a JVM baseline but they do not exercise the SQLCipher code path
   that production Android clients actually hit.

2. An `@Ignore`d message DAO benchmark test in `:data:persistence` `commonTest`. On Android it does
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
benchmarks. Message scenarios run in both runners from the same fixtures, so we have a direct
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
- **Encryption path**: benchmarks build the user DB via `userDatabaseBuilder` with a fixed raw
  `UserDBSecret`. libsqlcipher is loaded and `SupportOpenHelperFactory` drives the connection —
  this is the production path.
- **Storage**: the production database location from `PlatformDatabaseData(context)`.
  `BenchmarkDbRule` deletes it before each case and nukes it afterwards to keep runs clean.
- **Scenarios**: message insert, paging, mark-read, asset status and concurrent read/write, the
  conversation list, and message timelines. [The module README](../../test/android-benchmarks/README.md)
  lists them. Message data comes from `test/benchmark-fixtures`, which both runners compile.
- **Dependencies added** (to `gradle/libs.versions.toml`):
  - the `androidx-benchmark` version key, on the 1.5 line: 1.4.x targets the old AGP
    `TestedExtension` DSL and is incompatible with AGP 9.0
  - `androidx.benchmark:benchmark-junit4` library
  - `androidx.benchmark` Gradle plugin
  - `androidx.test.ext:junit` (adds `AndroidJUnit4` runner; not previously in the catalog)
- **Run command**:
  ```
  ./gradlew :test:android-benchmarks:connectedReleaseAndroidTest
  ```
  Results land in `build/outputs/connected_android_test_additional_output/.../benchmarkData.json`.
- **Out of scope for this ADR**: CI integration, iOS/XCTest measurement, and folding into
  `:test:benchmarks` as a KMP target. Any of those come as separate ADRs
  once this module proves its worth.

**Module dependency direction:** `:test:android-benchmarks` depends on `:data:persistence`. It is a
leaf test module — nothing depends on it. It does not violate the existing layering rules.

## Consequences

**Easier:**

- Repeatable on-device measurement of the real SQLCipher path for message insert/select, with
  proper warmup, iteration control, and JSON output.
- A direct JVM-plain (JMH) ↔ Android-encrypted (androidx.benchmark) comparison for the shared
  message scenarios, which previously required reading two different test files and squinting.
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