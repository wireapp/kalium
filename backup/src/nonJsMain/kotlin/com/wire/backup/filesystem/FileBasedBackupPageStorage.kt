/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.backup.filesystem

import okio.FileSystem
import okio.Path
import okio.Source
import okio.use

internal class FileBasedBackupPageStorage(
    private val fileSystem: FileSystem,
    private val workDirectory: Path,
    shouldBeCleared: Boolean,
) : BackupPageStorage {

    init {
        if (!fileSystem.exists(workDirectory)) {
            fileSystem.createDirectories(workDirectory)
        }
        if (shouldBeCleared) {
            clear()
        }
        require(fileSystem.metadata(workDirectory).isDirectory) {
            "Provided work directory is not a directory! ($workDirectory)"
        }
    }

    override fun persistEntry(backupPage: BackupPage) {
        check(!fileSystem.exists(workDirectory / backupPage.name)) {
            "File with name ${backupPage.name} already exists!"
        }
        fileSystem.write(workDirectory / backupPage.name) {
            writeAll(backupPage.data)
        }
    }

    override operator fun get(entryName: String): BackupPage? {
        val entryFile = workDirectory / entryName
        return if (fileSystem.exists(entryFile)) {
            withReadonlySource(entryFile) { source ->
                source.use {
                    BackupPage(entryName, it)
                }
            }
        } else {
            null
        }
    }

    override fun listEntries(): List<BackupPage> =
        fileSystem.list(workDirectory).map { dir ->
            withReadonlySource(dir) { source ->
                    BackupPage(dir.name, source)
            }
        }

    private inline fun <T> withReadonlySource(path: Path, block: (Source) -> T): T =
        fileSystem.openReadOnly(path).use { file ->
            block(file.source())
        }

    override fun clear() {
        fileSystem.list(workDirectory).forEach { fileSystem.delete(it) }
    }
}
