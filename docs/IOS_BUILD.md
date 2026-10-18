# Building Kalium for Apple platforms

This guide covers building Kalium's Apple frameworks and running its Apple tests.

## Requirements

- macOS on Apple Silicon (Intel Macs are not supported)
- JDK 21
- Xcode with command-line tools (`xcode-select --install`)
- Python 3, only for the simulator test runner

## Targets

Every library module built with the `kalium.library` convention plugin enables these
targets (`buildSrc/src/main/kotlin/com/wire/kalium/plugins/CommonDarwinConfig.kt`):

| Target | Use |
| --- | --- |
| `iosArm64` | iPhone and iPad devices |
| `iosSimulatorArm64` | iOS Simulator on Apple Silicon |
| `macosArm64` | macOS on Apple Silicon |

Apple binaries link the system SQLite (`-lsqlite3`). Shared Apple code lives in
`appleMain`, iOS-only code in `iosMain`, and tests in `appleTest` or `iosTest`.

## Native dependencies

- **CoreCrypto** comes from `com.wire:core-crypto-kmp` and is linked statically.
- **AVS** bindings come from `com.wire:avs-kmp`, but the runtime is the dynamic
  `avs.framework`, which the app or extension must embed. The
  `com.wire.kalium.apple-avs-runtime` plugin (an included build in
  `plugins/apple-avs-runtime`) downloads, verifies, links and embeds the matching
  runtime. `:logic`, `:nse`, `:share`, `:domain:calling`,
  `:domain:calling-notifications` and `:domain:messaging:receiving` apply it. See the
  [plugin guide](../plugins/apple-avs-runtime/README.md).

Both versions are pinned in `gradle/libs.versions.toml` (`core-crypto` and `avs`).

## Frameworks

Three modules produce frameworks for Swift:

| Module | Framework | Targets | For |
| --- | --- | --- | --- |
| `:logic` | `KaliumLogic` | iOS device, iOS simulator, macOS | The app |
| `:nse` | `KaliumNSE` | iOS device, iOS simulator | The Notification Service Extension ([guide](../nse/README.md)) |
| `:share` | `KaliumShare` | iOS device, iOS simulator | The share extension ([guide](../share/README.md)) |

Build an XCFramework with all its slices:

```bash
./gradlew :logic:assembleKaliumLogicDebugXCFramework
./gradlew :logic:assembleKaliumLogicReleaseXCFramework

./gradlew :nse:assembleKaliumNSEReleaseXCFramework
./gradlew :share:assembleKaliumShareReleaseXCFramework
```

Gradle writes each XCFramework to `<module>/build/XCFrameworks/<debug|release>/`.

`KaliumNSE` and `KaliumShare` are linked with `-application_extension`. Pass
`-Pwire.ios.simulatorOnly=true` or `-Pwire.ios.deviceOnly=true` to put only one slice
in their XCFrameworks and skip linking the other. The two flags cannot be combined, and
`:logic` ignores them.

To build a single framework instead, use the Kotlin link task for one target:

```bash
./gradlew :logic:linkDebugFrameworkIosSimulatorArm64
# logic/build/bin/iosSimulatorArm64/debugFramework/KaliumLogic.framework
```

## Using the frameworks in Xcode

1. Build the XCFramework for the configuration you need.
2. Add it to the target under **Frameworks, Libraries, and Embedded Content** and set
   **Embed** to **Embed & Sign**.
3. Embed AVS for the module that produced the framework. Generate its Xcode
   configuration once after cloning, updating or cleaning:

   ```bash
   ./gradlew :logic:generateAvsXcodeConfig
   ```

   Include `logic/build/kalium-apple-avs-runtime/xcode/KaliumAvsRuntime.xcconfig` in the
   target's configuration, then add a Run Script phase after the framework is embedded:

   ```bash
   KALIUM_DIR="$SRCROOT/Frameworks/kalium"
   "$KALIUM_DIR/gradlew" -p "$KALIUM_DIR" :logic:embedAvsForXcode
   ```

   Extensions use the same tasks on their own module, for example
   `:nse:generateAvsXcodeConfig` and `:nse:embedAvsForXcode`. Copying only the Kalium
   framework does not include AVS.
4. Import the module by its framework name:

   ```swift
   import KaliumLogic
   ```

The [plugin guide](../plugins/apple-avs-runtime/README.md) covers consumers that build
their own KMP framework, offline archives, caching and dSYMs.

### Kalium as a Git submodule

```bash
git submodule add https://github.com/wireapp/kalium.git Frameworks/kalium
git submodule update --init --recursive
```

Run the Gradle commands above with `Frameworks/kalium/gradlew -p Frameworks/kalium`.
Pinning the submodule also pins CoreCrypto, the AVS bindings and the AVS runtime.

## Running Apple tests

Run one module's tests with its target test task:

```bash
./gradlew :core:util:iosSimulatorArm64Test
./gradlew :share:macosArm64Test
```

Tests in `:core:cryptography`, `:domain:messaging:receiving` and `:logic` use the
simulator Keychain, which a standalone simulator process cannot reach, so they need a
booted simulator (`standalone = false`). CI runs all simulator tests with
`nse/scripts/run-ios-tests.py`. It creates a disposable iPhone simulator, trusts a local
X.509 fixture certificate, starts the fixture server and runs
`./gradlew iosSimulatorArm64Test --device <simulator>`, forwarding any extra arguments to
Gradle:

```bash
python3 -m venv .venv
.venv/bin/pip install -r nse/scripts/requirements-ios-tests.txt
.venv/bin/python nse/scripts/run-ios-tests.py --continue
```

`./gradlew iOSOnlyAffectedTest` runs `iosSimulatorArm64Test` for the affected modules when
affected-module data is available, and for all modules otherwise.

## Troubleshooting

- **SQLite or Security link errors:** install the Xcode command-line tools and check
  `xcode-select -p`.
- **No simulator found:** install an iOS Simulator runtime for the selected Xcode and check
  `xcrun simctl list runtimes`.
- **AVS missing at launch:** the target did not run `embedAvsForXcode` for the module that
  produced its framework.
- **Listing tasks:** `./gradlew :logic:tasks --all | grep -i xcframework`.
