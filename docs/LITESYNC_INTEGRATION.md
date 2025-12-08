# LiteSync Integration Guide

## Overview

Kalium now supports optional [LiteSync](https://litesync.io) integration for SQLite database synchronization. When configured, Kalium can act as a LiteSync secondary node that synchronizes its database with a LiteSync primary node.

**Important Notes:**
- LiteSync integration is **optional**. If not configured, Kalium uses standard SQLite.
- LiteSync is **supported on JVM and Android** platforms.
- iOS/macOS support requires additional native library integration (not yet implemented).

## What is LiteSync?

LiteSync is a SQLite replication and synchronization library that enables applications to keep their databases synchronized across multiple nodes. It supports:
- Real-time bidirectional synchronization
- Offline operation with automatic sync on reconnection
- Multiple synchronization topologies (centralized, peer-to-peer, mixed)

Learn more at: https://litesync.io

## Requirements

### JVM Platform

1. **LiteSync JDBC Driver**: Replace the standard SQLite JDBC driver with the LiteSync-enabled version.
   - Download from: https://litesync.io/en/download.html
   - **Note**: The free version has limitations (single table + no encryption)
   - For Kalium's use case (multiple tables + encryption), you'll need the commercial version

2. **Primary Node**: A running LiteSync primary node that the Kalium instance will connect to.

### Android Platform

1. **LiteSync Native Libraries**: Replace the standard SQLite/SQLCipher libraries with LiteSync-enabled versions.
   - Download from: https://litesync.io/en/download.html
   - You need the `.so` files for all supported architectures (armeabi-v7a, arm64-v8a, x86, x86_64)
   - Place them in `src/main/jniLibs/[arch]/` in your Android project
   - **Note**: The free version has limitations (single table + no encryption)
   - For Kalium's use case (multiple tables + encryption), you'll need the commercial version

2. **Library Loading**: Kalium automatically handles library loading:
   - For encrypted databases: The code loads `sqlcipher` (replace with LiteSync-enabled build)
   - For non-encrypted databases: The code loads `sqlite3` (replace with LiteSync-enabled build)
   - You must replace these libraries with LiteSync versions in your app's native libraries

3. **Primary Node**: A running LiteSync primary node that the Kalium instance will connect to.

### iOS/macOS Platform

⚠️ **Not yet implemented**. iOS support requires:
- LiteSync native framework for iOS/macOS
- Modified database configuration to pass LiteSync parameters
- See the TODO comments in `data/persistence/src/appleMain/kotlin/com/wire/kalium/persistence/db/PlatformDatabaseData.kt`

## Configuration

There are two ways to configure LiteSync in Kalium:

### Option 1: Programmatic Configuration (Recommended)

Configure LiteSync when creating the `CoreLogic` instance:

```kotlin
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.featureFlags.LiteSyncConfig

val kaliumConfigs = KaliumConfigs(
    liteSyncConfig = LiteSyncConfig(
        primaryAddress = "tcp://litesync-server.example.com:1234",
        nodeType = LiteSyncConfig.NodeType.SECONDARY,
        enabled = true
    )
)

val coreLogic = CoreLogic(
    userAgent = "MyApp/1.0",
    kaliumConfigs = kaliumConfigs
)
```

### Option 2: Environment Variable

Set the `KALIUM_LITESYNC_PRIMARY` environment variable:

```bash
export KALIUM_LITESYNC_PRIMARY="tcp://litesync-server.example.com:1234"
```

Then access it in your code:

```kotlin
val liteSyncConfig = LiteSyncConfig.fromEnvironment()
val kaliumConfigs = KaliumConfigs(
    liteSyncConfig = liteSyncConfig
)
```

**Note**: Environment variable configuration works on JVM and iOS/macOS platforms. Android should use programmatic configuration.

## Configuration Options

### `LiteSyncConfig`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `primaryAddress` | `String` | *required* | Address of the LiteSync primary node (e.g., `tcp://server:1234`) |
| `nodeType` | `NodeType` | `SECONDARY` | Node type: `PRIMARY` or `SECONDARY` |
| `enabled` | `Boolean` | `true` | Whether LiteSync synchronization is enabled |

### `NodeType` Enum

- **`SECONDARY`**: Connects to a primary node. Typical use case for Kalium clients.
- **`PRIMARY`**: Binds to an address and accepts connections from secondary nodes.

## How It Works

When LiteSync is configured:

### JVM Platform

1. **Database URI Modification**: Kalium appends LiteSync connection parameters to the SQLite database URI:
   - Standard: `jdbc:sqlite:/path/to/database.db`
   - With LiteSync: `jdbc:sqlite:/path/to/database.db?node=secondary&connect=tcp://server:1234`

2. **Driver Configuration**: The modified URI is passed to the SQLite JDBC driver, which should be the LiteSync-enabled version.

### Android Platform

1. **Database Name Modification**: Kalium appends LiteSync connection parameters to the database name:
   - Standard: `userdb.db`
   - With LiteSync: `userdb.db?node=secondary&connect=tcp://server:1234`

2. **Native Library**: The LiteSync-enabled native library (`.so` file) interprets the connection parameters when opening the database.

3. **Library Loading**:
   - For encrypted databases: Loads the LiteSync-enabled `sqlcipher` library
   - For non-encrypted databases: Loads the LiteSync-enabled `sqlite3` library

### All Platforms

**Synchronization**: The LiteSync driver handles:
- Initial database replication from the primary node
- Real-time synchronization of changes
- Offline operation with buffered changes
- Automatic reconnection and sync

## Platform Support Matrix

| Platform | Status | Notes |
|----------|--------|-------|
| JVM | ✅ Supported | Requires LiteSync JDBC driver |
| Android | ✅ Supported | Requires LiteSync native libraries (.so files) |
| iOS/macOS | ❌ Not Implemented | Requires native framework integration |
| JavaScript | ❌ Not Supported | Not planned |

## Limitations

### Free Version Limitations

The free LiteSync version has these limitations:
- **Single table per database** - Kalium uses multiple tables
- **No encryption** - Kalium uses SQLCipher encryption on some platforms

**Recommendation**: For production use with Kalium, you'll need the commercial LiteSync license.

### Current Implementation Limitations

- JVM and Android platforms are supported; iOS/macOS require additional work
- Encryption is not supported on JVM (general Kalium limitation, not LiteSync-specific)
- On Android, you must provide LiteSync native libraries that replace the standard SQLite/SQLCipher libraries
- No migration path for existing databases (you'll need to start fresh or manually migrate)

## Example: JVM Application with LiteSync

```kotlin
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.featureFlags.LiteSyncConfig

fun main() {
    // Configure LiteSync
    val liteSyncConfig = LiteSyncConfig(
        primaryAddress = "tcp://192.168.1.100:1234",
        nodeType = LiteSyncConfig.NodeType.SECONDARY
    )

    // Create CoreLogic with LiteSync enabled
    val kaliumConfigs = KaliumConfigs(
        liteSyncConfig = liteSyncConfig
    )

    val coreLogic = CoreLogic(
        userAgent = "WireJVM/1.0",
        kaliumConfigs = kaliumConfigs
    )

    // Use Kalium as normal - synchronization happens automatically
    // ...
}
```

## Example: Android Application with LiteSync

### Step 1: Add LiteSync Native Libraries

Download LiteSync Android libraries from https://litesync.io/en/download.html and place them in your Android project:

```
your-android-app/
├── src/main/jniLibs/
│   ├── armeabi-v7a/
│   │   └── libsqlcipher.so  (LiteSync-enabled version)
│   ├── arm64-v8a/
│   │   └── libsqlcipher.so  (LiteSync-enabled version)
│   ├── x86/
│   │   └── libsqlcipher.so  (LiteSync-enabled version)
│   └── x86_64/
│       └── libsqlcipher.so  (LiteSync-enabled version)
```

**Important**: Replace the standard SQLCipher library with the LiteSync-enabled version. The library filename must match what Kalium expects (`libsqlcipher.so` for encrypted databases, or `libsqlite3.so` for non-encrypted).

### Step 2: Configure LiteSync in Your Android App

```kotlin
import android.app.Application
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.featureFlags.LiteSyncConfig

class MyApplication : Application() {
    lateinit var coreLogic: CoreLogic

    override fun onCreate() {
        super.onCreate()

        // Configure LiteSync
        val liteSyncConfig = LiteSyncConfig(
            primaryAddress = "tcp://litesync-server.example.com:1234",
            nodeType = LiteSyncConfig.NodeType.SECONDARY,
            enabled = true
        )

        // Create CoreLogic with LiteSync enabled
        val kaliumConfigs = KaliumConfigs(
            shouldEncryptData = true,  // Use SQLCipher encryption
            liteSyncConfig = liteSyncConfig
        )

        coreLogic = CoreLogic(
            appContext = this,
            userAgent = "MyAndroidApp/1.0",
            kaliumConfigs = kaliumConfigs
        )

        // Use Kalium as normal - synchronization happens automatically
    }
}
```

### Step 3: Verify Library Loading

Kalium will automatically load the LiteSync library. Check logcat for confirmation:

```
System.loadLibrary: sqlcipher
```

If you see errors, ensure:
1. LiteSync `.so` files are in the correct `jniLibs` directories
2. The library filename matches what Kalium expects
3. All required architectures are included

## Troubleshooting

### Android: UnsatisfiedLinkError or Library Loading Errors

**Cause**: LiteSync native libraries are not properly installed or have wrong filename.

**Solution**:
1. Ensure LiteSync `.so` files are placed in `src/main/jniLibs/[arch]/`
2. Verify the library filename matches:
   - For encrypted databases: `libsqlcipher.so` (LiteSync-enabled version)
   - For non-encrypted databases: `libsqlite3.so` (LiteSync-enabled version)
3. Include all required architectures (arm64-v8a, armeabi-v7a, x86, x86_64)
4. Rebuild and reinstall your Android app

### Database Connection Fails

**Possible causes**:
1. **JVM**: LiteSync JDBC driver not in classpath
2. **Android**: LiteSync native library not replaced or incorrect
3. Primary node not reachable
4. Incorrect primary address format

**Solutions**:
1. **JVM**: Ensure LiteSync JDBC driver is added to dependencies
2. **Android**: Verify LiteSync `.so` files are in `jniLibs` and properly named
3. Verify network connectivity to primary node
4. Check address format: `tcp://hostname:port`

### Synchronization Not Working

**Possible causes**:
1. Free version limitations (multiple tables)
2. Primary node not properly configured
3. Firewall blocking connection

**Solutions**:
1. Verify you're using the commercial LiteSync version
2. Check primary node configuration and logs
3. Ensure port is accessible (check firewall rules)

## Disabling LiteSync

To disable LiteSync and use standard SQLite:

```kotlin
val kaliumConfigs = KaliumConfigs(
    liteSyncConfig = null  // or simply omit this parameter
)
```

Or unset the environment variable:

```bash
unset KALIUM_LITESYNC_PRIMARY
```

## Additional Resources

- LiteSync Official Website: https://litesync.io
- LiteSync Documentation: https://litesync.io/en/sqlite-synchronization.html
- LiteSync Downloads: https://litesync.io/en/download.html
- Kalium Repository: https://github.com/wireapp/kalium

## Contributing

To add Android/iOS LiteSync support, see the TODO comments in:
- `data/persistence/src/androidMain/kotlin/com/wire/kalium/persistence/db/PlatformDatabaseData.kt`
- `data/persistence/src/appleMain/kotlin/com/wire/kalium/persistence/db/PlatformDatabaseData.kt`

Key tasks:
1. Integrate LiteSync native libraries for each platform
2. Modify database opening code to support LiteSync connection parameters
3. Test synchronization functionality
4. Update this documentation with platform-specific instructions
