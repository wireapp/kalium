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

import com.wire.backup.compression.Zipper
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.filesystem.BackupEntry
import com.wire.backup.filesystem.EntryStorage
import com.wire.backup.filesystem.FileBasedEntryStorage
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Source

public actual class MPBackupExporter(
    selfUserId: BackupQualifiedId,
    private val workDirectory: String,
    private val outputDirectory: String,
    private val fileZipper: FileZipper
) : CommonMPBackupExporter(selfUserId) {

    private val fileSystem = FileSystem.SYSTEM

    override val storage: EntryStorage = FileBasedEntryStorage(
        fileSystem, workDirectory.toPath()
    )

    override val zipper: Zipper = object : Zipper {
        override fun compress(data: List<BackupEntry>): Source {
            val entries = data.map { fileSystem.canonicalize(workDirectory.toPath() / it.name).toString() }
            val pathToZippedArchive = fileZipper.zip(entries).toPath()
            return fileSystem.source(pathToZippedArchive)
                .also { fileSystem.delete(pathToZippedArchive) }
        }
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
