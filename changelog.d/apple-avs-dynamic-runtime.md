### Changed
- Apple builds now link AVS dynamically through `com.wire.kalium.apple-avs-runtime`, with the compatible AVS version and checksum owned by Kalium.
- Removed the Apple no-op AVS implementation and the `kalium.disableAppleAvs` source-build opt-out; Apple builds now always compile and link the real AVS bridge.

### Migration
Apply the plugin to the KMP module producing the Apple framework. Git-submodule consumers use the plugin bundled with Kalium, which is already applied to `:logic`; Maven consumers use the matching published plugin version. Generate and include `KaliumAvsRuntime.xcconfig`, then run that module's `embedAvsForXcode` task from Xcode.

### Compatibility
ABI: unchanged.
Source: SDK APIs are unchanged; Apple build integration requires migration.
Behavior: AVS is now a required, separate verified dynamic framework; the Apple no-op implementation and `kalium.disableAppleAvs` source-build opt-out have been removed. Apple applications that do not link and embed AVS will fail to build or run calling features. AVS 10.4.32 raises the minimum supported macOS version to 15.0. Gradle caches the verified runtime by checksum across modules and project cleans; AVS dSYMs are extracted only by the explicit `extractAvsDebugSymbols` task.
