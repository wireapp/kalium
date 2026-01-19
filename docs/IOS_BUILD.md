# Building Kalium for iOS

This guide covers building Kalium for iOS targets using Kotlin Multiplatform.

## Requirements

- **macOS on Apple Silicon** (Intel Macs are not supported)
- **JDK 21** (e.g., openjdk-21-jdk)
- **Xcode** with command-line tools installed
- **CocoaPods** (optional, for framework integration)

## Build Configuration

iOS builds require the unified CoreCrypto KMP library. You must ensure the `USE_UNIFIED_CORE_CRYPTO` property is set to `true`.

### Setting the Property

**Option 1: In `gradle.properties`**

Ensure your `gradle.properties` file contains:

```properties
USE_UNIFIED_CORE_CRYPTO=true
```

**Option 2: Via command line**

Pass the property when running Gradle commands:

```bash
./gradlew :logic:linkDebugFrameworkIosArm64 -PUSE_UNIFIED_CORE_CRYPTO=true
```

### Why This Is Required

The unified CoreCrypto KMP library (`com.wire:core-crypto-kmp`) provides multiplatform support including iOS targets. The legacy non-unified library (`com.wire:core-crypto-android`) only supports Android and will cause iOS builds to fail.

## Supported iOS Targets

Kalium supports the following Apple targets:

| Target | Architecture | Description |
|--------|-------------|-------------|
| `iosArm64` | ARM64 | Physical iOS devices (iPhone, iPad) |
| `iosSimulatorArm64` | ARM64 | iOS Simulator on Apple Silicon Macs |
| `macosArm64` | ARM64 | macOS on Apple Silicon |

## Build Commands

### Building Libraries

Build the Kotlin library for a specific iOS target:

```bash
# For physical iOS devices (ARM64)
./gradlew :logic:compileKotlinIosArm64

# For iOS Simulator on Apple Silicon Mac
./gradlew :logic:compileKotlinIosSimulatorArm64
```

### Building All iOS Targets

To compile all iOS targets for a module:

```bash
./gradlew :logic:compileKotlinIosArm64 :logic:compileKotlinIosSimulatorArm64
```

### Running iOS Tests

Run tests on the iOS simulator:

```bash
# On Apple Silicon Mac (recommended)
./gradlew iosSimulatorArm64Test

# Run tests for a specific module
./gradlew :core:cryptography:iosSimulatorArm64Test
./gradlew :data:persistence:iosSimulatorArm64Test
```

### Building Frameworks

Build a debug framework for integration with Xcode projects:

```bash
# For iOS device
./gradlew :logic:linkDebugFrameworkIosArm64

# For iOS Simulator (Apple Silicon)
./gradlew :logic:linkDebugFrameworkIosSimulatorArm64

```

Build a release framework:

```bash
./gradlew :logic:linkReleaseFrameworkIosArm64
./gradlew :logic:linkReleaseFrameworkIosSimulatorArm64
```

## Module-Specific Notes

### Core Cryptography Module

The `:core:cryptography` module has additional iOS-specific configuration:

- Links against the `Security` framework for cryptographic operations
- Uses libsodium bindings for multiplatform cryptography

```kotlin
iosArm64 {
    binaries.all {
        linkerOpts("-framework", "Security")
    }
}
```

### All Native Targets

All native (Apple) targets link against SQLite:

```kotlin
linkerOpts("-lsqlite3")
```

## Project Structure

iOS-specific source code is organized in the following source sets:

```
module/
├── src/
│   ├── commonMain/       # Shared code across all platforms
│   ├── commonTest/       # Shared tests
│   ├── appleMain/        # Shared code for all Apple platforms (iOS + macOS)
│   ├── iosMain/          # iOS-specific code (all iOS targets)
│   ├── iosArm64Main/     # iOS device-specific code
│   ├── iosSimulatorArm64Main/  # iOS Simulator (Apple Silicon) specific code
│   └── iosTest/          # iOS-specific tests
```

## Integrating with iOS Projects

### Using the Framework

After building the framework, locate it at:

```
logic/build/bin/iosArm64/debugFramework/logic.framework
# or
logic/build/bin/iosSimulatorArm64/debugFramework/logic.framework
```

Add the framework to your Xcode project:

1. Drag the `.framework` into your Xcode project
2. Ensure it's added to "Frameworks, Libraries, and Embedded Content"
3. Set "Embed" to "Embed & Sign"

### Using with CocoaPods

For CocoaPods integration, you may need to configure a podspec. The Kotlin Multiplatform Gradle plugin can generate one:

```bash
./gradlew :logic:podspec
```

## Using Kalium as a Git Submodule

If you want to include Kalium directly in your iOS project as a submodule, follow these steps.

### Adding the Submodule

```bash
cd /path/to/your/ios-project
git submodule add https://github.com/wireapp/kalium.git Frameworks/kalium
git submodule update --init --recursive
```

### Project Structure

Your iOS project structure should look like:

```
YourIOSApp/
├── YourIOSApp/
│   ├── AppDelegate.swift
│   └── ...
├── YourIOSApp.xcodeproj
├── Frameworks/
│   └── kalium/                 # Kalium submodule
│       ├── logic/
│       ├── core/
│       ├── gradlew
│       └── ...
└── ...
```

### Building the Framework from Submodule

Navigate to the submodule and build the framework:

```bash
cd Frameworks/kalium

# Build for iOS Simulator (Apple Silicon)
./gradlew :logic:linkDebugFrameworkIosSimulatorArm64

# Build for iOS device
./gradlew :logic:linkDebugFrameworkIosArm64
```

### Xcode Configuration

#### Option 1: Manual Framework Integration

1. In Xcode, go to your target's **General** tab
2. Under **Frameworks, Libraries, and Embedded Content**, click **+**
3. Click **Add Other...** → **Add Files...**
4. Navigate to `Frameworks/kalium/logic/build/bin/iosSimulatorArm64/debugFramework/`
5. Select `logic.framework` and click **Add**
6. Set **Embed** to **Embed & Sign**

Add the framework search path in **Build Settings**:

```
FRAMEWORK_SEARCH_PATHS = $(PROJECT_DIR)/Frameworks/kalium/logic/build/bin/$(CURRENT_ARCH)/debugFramework
```

#### Option 2: Build Phase Script (Recommended)

Add a **Run Script** build phase that builds the framework automatically:

1. In Xcode, select your target → **Build Phases**
2. Click **+** → **New Run Script Phase**
3. Move it **before** "Compile Sources"
4. Add the following script:

```bash
#!/bin/bash
set -e

KALIUM_DIR="${PROJECT_DIR}/Frameworks/kalium"
cd "$KALIUM_DIR"

# Determine which target to build based on the SDK
if [ "$PLATFORM_NAME" = "iphonesimulator" ]; then
    TARGET="iosSimulatorArm64"
else
    TARGET="iosArm64"
fi

# Build configuration
if [ "$CONFIGURATION" = "Release" ]; then
    BUILD_TYPE="Release"
else
    BUILD_TYPE="Debug"
fi

echo "Building Kalium for $TARGET ($BUILD_TYPE)..."
./gradlew :logic:link${BUILD_TYPE}Framework${TARGET^} --no-daemon -q

# Copy framework to derived data
FRAMEWORK_SRC="$KALIUM_DIR/logic/build/bin/$TARGET/${BUILD_TYPE,,}Framework/logic.framework"
FRAMEWORK_DST="${BUILT_PRODUCTS_DIR}/logic.framework"

if [ -d "$FRAMEWORK_SRC" ]; then
    rm -rf "$FRAMEWORK_DST"
    cp -R "$FRAMEWORK_SRC" "$FRAMEWORK_DST"
    echo "Framework copied to $FRAMEWORK_DST"
fi
```

5. Add input files (for incremental builds):
   ```
   $(PROJECT_DIR)/Frameworks/kalium/logic/src/
   ```

6. Add output files:
   ```
   $(BUILT_PRODUCTS_DIR)/logic.framework
   ```

### Creating an XCFramework

For distributing or supporting multiple architectures in a single bundle, create an XCFramework:

```bash
cd Frameworks/kalium

# Build all iOS frameworks
./gradlew :logic:linkReleaseFrameworkIosArm64
./gradlew :logic:linkReleaseFrameworkIosSimulatorArm64

# Create XCFramework
xcodebuild -create-xcframework \
    -framework logic/build/bin/iosArm64/releaseFramework/logic.framework \
    -framework logic/build/bin/iosSimulatorArm64/releaseFramework/logic.framework \
    -output logic/build/logic.xcframework
```

### Updating the Submodule

To update Kalium to the latest version:

```bash
cd Frameworks/kalium
git fetch origin
git checkout develop  # or a specific tag/commit
git pull

# Return to your project root
cd ../..
git add Frameworks/kalium
git commit -m "Update Kalium submodule"
```

### Swift Package Manager (Experimental)

If you prefer SPM over submodules, you can reference the built XCFramework:

1. Build the XCFramework as shown above
2. Create a `Package.swift` wrapper in your project:

```swift
// swift-tools-version:5.7
import PackageDescription

let package = Package(
    name: "KaliumWrapper",
    platforms: [.iOS(.v14)],
    products: [
        .library(name: "Kalium", targets: ["Kalium"])
    ],
    targets: [
        .binaryTarget(
            name: "Kalium",
            path: "Frameworks/kalium/logic/build/logic.xcframework"
        )
    ]
)
```

### Importing in Swift

Once integrated, import and use Kalium in your Swift code:

```swift
import KaliumLogic

// Use Kalium classes and functions
// Note: Kotlin classes are accessible with their full package path
```

## Troubleshooting

### Common Issues

**Build fails with missing SQLite:**

Ensure Xcode command-line tools are installed:
```bash
xcode-select --install
```

**Simulator tests fail to run:**

1. Ensure you have a simulator runtime installed matching your target
2. Check available simulators: `xcrun simctl list devices`

**Framework not found in Xcode:**

1. Verify the framework was built successfully
2. Check the framework search paths in Xcode build settings
3. Ensure you're building on Apple Silicon (Intel Macs are not supported)

### Checking Available Tasks

List all iOS-related Gradle tasks:

```bash
./gradlew tasks --all | grep -i ios
```

## Current Status

iOS support in Kalium is partial. The following modules have iOS targets enabled:

- `:core:cryptography`
- `:core:logger`
- `:core:util`
- `:data:persistence`
- `:data:network`
- `:data:protobuf`
- `:domain:backup`
- `:logic`

Some features may have limited or no implementation on iOS. Check individual module source sets for platform-specific implementations.
