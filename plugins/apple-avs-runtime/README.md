# Kalium Apple AVS runtime plugin

`com.wire.kalium.apple-avs-runtime` downloads and verifies the AVS
XCFramework, links it to Kotlin/Native binaries, and provides Xcode tasks for
embedding and signing it. The matching AVS version and checksum are baked into
the plugin; consumers must not configure them. The plugin also propagates the
minimum macOS version required by the selected AVS binary to Kotlin/Native and
the generated Xcode configuration. AVS 10.4.32 requires macOS 15.0.

## Kalium as a Git submodule

Kalium includes the plugin and applies it to `:logic`. If the application's
own KMP module produces the final framework, expose the bundled plugin with
`pluginManagement.includeBuild("Frameworks/kalium/plugins/apple-avs-runtime")`
and apply it to that module too.

Generate the Xcode configuration after cloning, updating, or cleaning the
submodule:

```bash
Frameworks/kalium/gradlew -p Frameworks/kalium :logic:generateAvsXcodeConfig
```

Include this file from the application `.xcconfig`:

```text
Frameworks/kalium/logic/build/kalium-apple-avs-runtime/xcode/KaliumAvsRuntime.xcconfig
```

Add a Run Script after the phase that builds `KaliumLogic.framework`:

```bash
KALIUM_DIR="$SRCROOT/Frameworks/kalium"
"$KALIUM_DIR/gradlew" -p "$KALIUM_DIR" :logic:embedAvsForXcode
```

Pinning the Kalium submodule also pins the AVS bindings, runtime, and checksum.

## Kalium from Maven Central

Apply the plugin to the consumer KMP module that produces the final Apple
framework. Use the same version for the plugin and Kalium:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}
```

```kotlin
// shared/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.wire.kalium.apple-avs-runtime") version "<kalium-version>"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.wire.kalium:logic:<kalium-version>")
        }
    }
}
```

For a module named `:shared`, generate:

```bash
./gradlew :shared:generateAvsXcodeConfig
```

Include the generated file:

```text
shared/build/kalium-apple-avs-runtime/xcode/KaliumAvsRuntime.xcconfig
```

Then run `:shared:embedAvsForXcode` from an Xcode Run Script so the task
receives Xcode's platform, destination, and signing environment.

> The Kalium Maven artifacts are published, but this plugin is not yet in
> Maven Central. This path becomes available when the plugin is published with
> the Kalium release. Until then, use the submodule integration.

## Optional local archive

CI or offline builds can provide the same official archive:

```properties
kalium.avs.archive=/absolute/path/avs.xcframework.zip
```

The plugin still verifies the baked checksum.

## Download and cache behavior

The first Apple build downloads and verifies the pinned AVS archive once. The
archive and the three runtime framework slices are stored by checksum under:

```text
$GRADLE_USER_HOME/caches/kalium-apple-avs-runtime/<sha256>/
```

All modules and concurrent Gradle builds reuse that content-addressed cache.
Project `clean` tasks do not remove it, and selecting an AVS release with a new
checksum creates a separate cache entry. No consumer configuration is needed.
Old checksum entries are not removed automatically. They can be deleted when
no build is using them and will be recreated on demand.

### Cache trust boundary

The archive is verified against the baked checksum every time it enters the
cache and again before it is unpacked; a mismatch discards the entry so the next
build re-downloads it. Extracted framework contents are re-hashed on cache hits
and compared with their ready marker, which detects incomplete writes and
accidental changes. The marker lives in the same directory as the binaries it
vouches for.

So anything able to write into the cache directory can substitute the framework
that later builds link and embed. Treat it exactly like the Gradle dependency
and plugin caches it sits next to: `GRADLE_USER_HOME` is trusted build input.
Concretely, do not share one cache directory across trust boundaries — a cache
writable by untrusted PR builds must not be readable by release builds. Give
each trust domain its own `kalium.avs.cacheDirectory`, and set
`kalium.avs.archive` when a build must not fetch from the network at all.

The runtime cache excludes AVS dSYMs because they are not used for linking or
embedding. Release and symbol-upload jobs can extract them explicitly from a
module that applies the plugin:

```bash
./gradlew :shared:extractAvsDebugSymbols
```

The task writes to
`shared/build/kalium-apple-avs-runtime/debug-symbols`. To place the persistent
cache elsewhere, set `kalium.avs.cacheDirectory` to an absolute path.

## Prebuilt XCFramework and SwiftPM

The Gradle plugin does not run for prebuilt-only consumers. SwiftPM must
declare both Kalium and AVS as binary targets; see
[ADR 0010](../../docs/adr/0010-distribute-kalium-and-avs-with-swift-package-manager.md).
