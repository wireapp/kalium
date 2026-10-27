# Validating the public failure boundary

The tighter boundary keeps `:data:data-mappers` off a JVM SDK consumer's compile classpath.
It replaces that public dependency with `:core:failures`; the SDK still compiles the adapters itself.

## Local incremental comparison

Compared the first PR #4615 revision (`929996442b08`, which exported `data-mappers`) with the
revised failure-contract boundary. Both checkouts used the same temporary Kotlin/JVM consumer with
`implementation(project(":logic"))` and this source:

```kotlin
import com.wire.kalium.common.error.CoreFailure

fun retryable(failure: CoreFailure): Boolean = failure.isRetryable
```

A temporary public class in `data-mappers` had one method returning an integer. After warming each
checkout, the runs changed nothing, changed that method's body, and then added an unused public
method to the class. The consumer's source never changed. Temporary sources were removed afterward.

Measured runs used Gradle 9.4.1, offline dependency resolution, a warm daemon, disabled configuration
cache, and `--no-build-cache`. The host was arm64 macOS 26.7.1 with a JDK 21 launcher; the temporary
consumer used a JDK 17 toolchain. Builds ran serially. Wall time includes Gradle startup/configuration
and all upstream tasks, not just consumer compilation.

| Change after warm-up | Previous boundary | Tight boundary | Previous wall time | Tight wall time |
| --- | --- | --- | --- | --- |
| None | Consumer `UP-TO-DATE` | Consumer `UP-TO-DATE` | 3.36 s | 3.19 s |
| Adapter method body | Consumer `UP-TO-DATE` | Consumer `UP-TO-DATE` | 3.17 s | 3.17 s |
| Adapter public method added | Consumer compile task executed | Consumer `UP-TO-DATE` | 4.70 s | 3.70 s |

These are single local observations, not a statistically established speedup. An executed Kotlin
compile task can still avoid recompiling individual files through incremental analysis. The useful
result is that the consumer task no longer runs for the unrelated adapter ABI change. The SDK's own
`:logic:compileKotlinJvm` task ran for that change in both versions.

The actual resolved consumer `compileClasspath` project components differed by exactly:

- Removed: `:data:data-mappers`.
- Added: `:core:failures`.

This does not claim that all SDK dependencies are hidden, that ordinary method-body edits become
faster, or that clean builds and Apple linking are faster. No full SDK clean-build speedup was measured.
The original core cleanup also reduced the `:core:common:compileKotlinJvm` dry-run graph from eleven
project compilations to four; that is a separate dependency-graph result against the pre-PR code.

## Reproducing the comparison

Use separate checkouts and add the same temporary `tools/build-boundary-consumer` project to each
(the settings script discovers its build file automatically):

```kotlin
plugins { id("org.jetbrains.kotlin.jvm") }
dependencies { implementation(project(":logic")) }
kotlin { jvmToolchain(17) }
```

Put the consumer source above in `src/main/kotlin`, and add an identical temporary public probe class
under `data/data-mappers/src/commonMain/kotlin`. Warm the build, then run the following after each
change while leaving all consumer sources untouched:

```sh
./gradlew :tools:build-boundary-consumer:compileKotlin \
  --offline --no-configuration-cache --no-build-cache \
  --console=plain -Dorg.gradle.logging.level=lifecycle
```

Record the consumer task outcome and wall time. Inspect the consumer `compileClasspath` using
Gradle's `dependencies` task or `incoming.resolutionResult`. Remove both temporary sources, rebuild,
and verify that the probe class is absent from the adapter jar before publishing any artifacts.
