package com.wire.helium.helpers

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

object Util {
    @JvmStatic
    @Throws(IOException::class)
    fun deleteDir(dir: String?) {
        val rootPath = Paths.get(dir)
        if (!rootPath.toFile().exists()) return
        Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
                .sorted(Comparator.reverseOrder())
                .map { obj: Path -> obj.toFile() }
                .forEach { obj: File -> obj.delete() }
    }
}