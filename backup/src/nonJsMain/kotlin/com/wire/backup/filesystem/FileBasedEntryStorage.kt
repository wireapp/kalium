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

internal class FileBasedEntryStorage(
    private val fileSystem: FileSystem,
    private val workDirectory: Path,
    shouldBeCleared: Boolean,
) : EntryStorage {

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

    override fun persistEntry(backupEntry: BackupEntry) {
        check(!fileSystem.exists(workDirectory / backupEntry.name)) {
            "File with name ${backupEntry.name} already exists!"
        }
        fileSystem.write(workDirectory / backupEntry.name) {
            writeAll(backupEntry.data)
        }
    }

    override operator fun get(entryName: String): BackupEntry? {
        val entryFile = workDirectory / entryName
        return if (fileSystem.exists(entryFile)) {
            BackupEntry(entryName, fileSystem.openReadOnly(entryFile).source())
        } else {
            null
        }
    }

    override fun listEntries(): List<BackupEntry> =
        fileSystem.list(workDirectory).map {
            BackupEntry(it.name, fileSystem.openReadOnly(it).source())
        }

    override fun clear() {
        fileSystem.list(workDirectory).forEach { fileSystem.delete(it) }
    }
}
