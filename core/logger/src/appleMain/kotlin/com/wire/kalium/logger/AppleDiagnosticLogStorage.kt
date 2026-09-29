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

package com.wire.kalium.logger

import co.touchlab.kermit.Severity
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.posix.LOCK_EX
import platform.posix.LOCK_NB
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_NOFOLLOW
import platform.posix.O_RDWR
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix.close
import platform.posix.flock
import platform.posix.open

/** All entry points run on their writer's serial queue, including directory creation and lock-file access. */
@OptIn(ExperimentalForeignApi::class)
internal class AppleDiagnosticLogStorage(
    rootPath: String,
    streamName: String,
    private val fileSystem: FileSystem,
    private val maxFileBytes: Long,
    private val rolledFiles: Int,
) {
    private val root = rootPath.toPath(normalize = true)
    private val directory = root / "logs"
    private val baseName = if (streamName == "app") "wire_logs" else "wire_logs-$streamName"
    private val active = directory / "$baseName.log"
    val displayPath: String = active.toString()

    init {
        require(root.isAbsolute) { "Diagnostic log root must be absolute" }
        require(streamName.matches(Regex("[a-z][a-z0-9_]*"))) { "Invalid diagnostic stream name" }
        require(maxFileBytes > 0 && rolledFiles > 0) { "Diagnostic retention limits must be positive" }
    }

    fun append(timestamp: String, severity: Severity, message: String, tag: String, throwable: Throwable?) = coordinated {
        prepareDirectory()
        requireRegularOrMissing(active)
        if ((fileSystem.metadataOrNull(active)?.size ?: 0) >= maxFileBytes) rotate()
        fileSystem.appendingSink(active).buffer().use { sink ->
            sink.writeUtf8("$timestamp ${severity.name} [$tag] $message\n")
            throwable?.let { sink.writeUtf8(it.stackTraceToString() + "\n") }
        }
    }

    fun checkAccess() = coordinated { prepareDirectory() }

    fun snapshot(destinationPath: String) = coordinated {
        prepareDirectory()
        val destination = destinationPath.toPath(normalize = true)
        require(destination.isAbsolute && !destination.toString().startsWith("$directory/")) {
            "Snapshot must be an absolute path outside the log directory"
        }
        fileSystem.createDirectories(destination, mustCreate = true)
        var completed = false
        try {
            logFiles().forEach { source -> fileSystem.copy(source, destination / source.name) }
            completed = true
        } finally {
            if (!completed) runCatching { fileSystem.deleteRecursively(destination) }
        }
    }

    fun delete() = coordinated {
        prepareDirectory()
        logFiles().forEach { fileSystem.delete(it) }
    }

    private fun logFiles(): List<Path> = fileSystem.list(directory).sorted().filter { source ->
        val metadata = fileSystem.metadata(source)
        source.name.matches(LOG_FILE_NAME) && metadata.isRegularFile && metadata.symlinkTarget == null
    }

    private fun prepareDirectory() {
        fileSystem.createDirectories(directory)
        val metadata = fileSystem.metadata(directory)
        if (!metadata.isDirectory || metadata.symlinkTarget != null) throw IOException("Log path is not a directory")
    }

    private fun requireRegularOrMissing(path: Path) {
        fileSystem.metadataOrNull(path)?.let {
            if (!it.isRegularFile || it.symlinkTarget != null) throw IOException("Log path is not a regular file")
        }
    }

    private fun rotate() {
        // Validate every path before mutation, so a link can never redirect append or rotation outside logs.
        for (index in 1..rolledFiles) requireRegularOrMissing(directory / "$baseName-$index.log")
        fileSystem.delete(directory / "$baseName-$rolledFiles.log", mustExist = false)
        for (index in rolledFiles - 1 downTo 1) {
            val source = directory / "$baseName-$index.log"
            if (fileSystem.exists(source)) fileSystem.atomicMove(source, directory / "$baseName-${index + 1}.log")
        }
        fileSystem.atomicMove(active, directory / "$baseName-1.log")
    }

    private inline fun <T> coordinated(block: () -> T): T {
        fileSystem.createDirectories(root)
        // Never delete or export this inode: every participating process must lock the same file.
        val descriptor = open((root / LOCK_FILE_NAME).toString(), O_CREAT or O_RDWR or O_CLOEXEC or O_NOFOLLOW, S_IRUSR or S_IWUSR)
        if (descriptor < 0) throw IOException("Diagnostic lock is unavailable")
        try {
            if (flock(descriptor, LOCK_EX or LOCK_NB) != 0) throw IOException("Diagnostic logs are busy")
            return block()
        } finally {
            // close releases flock. An interrupted close must not be retried with a potentially reused descriptor.
            close(descriptor)
        }
    }

    companion object {
        internal const val LOCK_FILE_NAME = ".diagnostic-logging.lock"
        private val LOG_FILE_NAME = Regex("wire_logs(?:-[a-z][a-z0-9_]*)?(?:-[1-9][0-9]*)?\\.log")
    }
}
