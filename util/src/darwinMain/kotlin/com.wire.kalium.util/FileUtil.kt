package com.wire.kalium.util

import platform.Foundation.NSFileManager

actual object FileUtil {
    actual fun mkDirs(path: String): Boolean =
        // TODO setup a logger for util and log on error?
        NSFileManager.defaultManager.createDirectoryAtPath(path, false, null, null)
}
