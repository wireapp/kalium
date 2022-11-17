package com.wire.kalium.logic.data.asset

import kotlin.jvm.JvmInline

@JvmInline
value class CacheFolder(val value: String)

@JvmInline
value class AssetsStorageFolder(val value: String)

@JvmInline
value class DBFolder(val value: String)

data class DataStoragePaths(val assetStoragePath: AssetsStorageFolder, val cachePath: CacheFolder, val dbPath: DBFolder)
