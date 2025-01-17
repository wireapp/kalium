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
package com.wire.backup.dump

import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.filesystem.BackupEntry
import com.wire.backup.filesystem.EntryStorage
import com.wire.backup.filesystem.FileBasedEntryStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Source

public actual class MPBackupExporter(
    selfUserId: BackupQualifiedId,
    workDirectory: String,
    private val outputDirectory: String,
    private val fileZipper: FileZipper
) : CommonMPBackupExporter(selfUserId) {

    private val workDirectoryPath = workDirectory.toPath() / "backupDump"

    private val fileSystem = FileSystem.SYSTEM

    override val storage: EntryStorage = FileBasedEntryStorage(
        fileSystem = fileSystem,
        workDirectory = workDirectoryPath,
        shouldBeCleared = true
    )

    override fun zipEntries(data: List<BackupEntry>): Deferred<Source> {
        val entries = data.map { fileSystem.canonicalize(workDirectoryPath / it.name).toString() }
        val pathToZippedArchive = fileZipper.zip(entries).toPath()
        return CompletableDeferred(
            fileSystem.source(pathToZippedArchive)
                .also { fileSystem.delete(pathToZippedArchive) }
        )
    }

    public suspend fun finalize(password: String?): String {
        val fileName = "export.wbu"
        val path = outputDirectory.toPath() / fileName
        fileSystem.delete(path)
        fileSystem.createDirectories(path.parent!!)
        val fileHandle = fileSystem.openReadWrite(path)
        finalize(password, fileHandle.sink())
        return path.toString()
    }
}
