/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.gradle.avs

import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

internal fun frameworkCacheMarker(directory: File, checksum: String): String =
    cacheMarker(checksum) + "contentsSha256=${directory.treeSha256()}\n"

private fun File.treeSha256(): String {
    val root = toPath().toRealPath()
    val digest = MessageDigest.getInstance("SHA-256")
    Files.walk(root).use { paths ->
        paths
            .filter { it != root && it.fileName.toString() != AVS_CACHE_READY_MARKER }
            .sorted(compareBy { root.relativize(it).toString() })
            .forEach { path -> digest.updateTreeEntry(root, path) }
    }
    return digest.digest().toHexString()
}

private fun MessageDigest.updateTreeEntry(root: Path, path: Path) {
    val relativePath = root.relativize(path).toString()
    require('\n' !in relativePath && '\u0000' !in relativePath) {
        "Invalid AVS cache entry path: '$relativePath'"
    }
    when {
        Files.isSymbolicLink(path) -> {
            val target = Files.readSymbolicLink(path)
            val resolvedTarget = path.parent.resolve(target).normalize().toRealPath()
            require(resolvedTarget.startsWith(root)) {
                "AVS cache symlink escapes the extracted framework directory: $relativePath -> $target"
            }
            update("link\u0000$relativePath\u0000$target\u0000".encodeToByteArray())
        }
        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ->
            update("directory\u0000$relativePath\u0000".encodeToByteArray())
        Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> {
            update("file\u0000$relativePath\u0000".encodeToByteArray())
            updateFileContents(path)
        }
        else -> throw GradleException("Unsupported AVS cache entry: $relativePath")
    }
}

private fun MessageDigest.updateFileContents(path: Path) {
    path.toFile().inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            update(buffer, 0, read)
        }
    }
}

private fun ByteArray.toHexString(): String =
    joinToString("") { byte -> "%02x".format(byte) }
