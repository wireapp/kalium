package com.wire.kalium.util

expect object FileUtil {
    fun mkDirs(path: String): Boolean
}
