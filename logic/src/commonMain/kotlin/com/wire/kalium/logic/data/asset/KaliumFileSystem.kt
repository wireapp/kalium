package com.wire.kalium.logic.data.asset

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

expect class KaliumFileSystem() : FileSystem

val kaliumFileSystem = KaliumFileSystem()

fun tempPath(): Path {
    val tempPath = "temp_path".toPath()
    if (!kaliumFileSystem.exists(tempPath))
        kaliumFileSystem.createDirectory(tempPath)
    return tempPath
}

fun deleteTempPath() {
    kaliumFileSystem.delete(tempPath())
}
