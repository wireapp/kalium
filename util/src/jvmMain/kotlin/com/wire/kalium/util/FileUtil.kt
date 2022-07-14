package com.wire.kalium.util

import java.io.File

actual object FileUtil {
    actual fun mkDirs(path: String): Boolean = File(path).mkdirs()
}
