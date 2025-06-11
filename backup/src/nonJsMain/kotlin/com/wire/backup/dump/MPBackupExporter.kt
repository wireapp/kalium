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

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupUser
import com.wire.backup.data.getBackupFileName
import com.wire.backup.filesystem.BackupPage
import com.wire.backup.filesystem.BackupPageStorage
import com.wire.backup.filesystem.FileBasedBackupPageStorage
import com.wire.kalium.protobuf.backup.BackupData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Source
import okio.use

/**
 * Entity able to serialize [BackupData] entities, like [BackupMessage], [BackupConversation], [BackupUser]
 * into a cross-platform [BackupData] format.
 * @sample samples.backup.BackupSamplesNonJS.exportBackup
 */
public actual class MPBackupExporter : CommonMPBackupExporter {
    private val outputDirectory: String
    private val fileZipper: FileZipper
    private val fileSystem: FileSystem

    public constructor(
        selfUserId: BackupQualifiedId,
        workDirectory: String,
        outputDirectory: String,
        fileZipper: FileZipper,
        fileSystem: FileSystem
    ) : super(selfUserId) {
        this.outputDirectory = outputDirectory
        this.fileZipper = fileZipper
        this.fileSystem = fileSystem
        this.workDirectoryPath = workDirectory.toPath() / "backupDump"
        this.storage = FileBasedBackupPageStorage(
            fileSystem = fileSystem,
            workDirectory = workDirectoryPath,
            shouldBeCleared = true
        )
    }

    public constructor(
        selfUserId: BackupQualifiedId,
        workDirectory: String,
        outputDirectory: String,
        fileZipper: FileZipper
    ) : this(selfUserId, workDirectory, outputDirectory, fileZipper, FileSystem.SYSTEM)

    private val workDirectoryPath: Path

    override val storage: BackupPageStorage

    override fun zipEntries(data: List<BackupPage>): Deferred<Source> {
        val entries = data.map { fileSystem.canonicalize(workDirectoryPath / it.name).toString() }
        val pathToZippedArchive = fileZipper.zip(entries, workDirectoryPath).toPath()
        return CompletableDeferred(
            fileSystem.source(pathToZippedArchive)
                .also { fileSystem.delete(pathToZippedArchive) }
        )
    }

    /**
     * Persists all the data into a single backup file, returning a [BackupExportResult].
     * This method should be called after all the data was added.
     * @param password optional password for the encryption. Can be an empty string, to export an unencrypted backup.
     */
    @Suppress("TooGenericExceptionCaught")
    public suspend fun finalize(password: String): BackupExportResult = try {
        val fileName = getBackupFileName()
        val path = outputDirectory.toPath() / fileName
        fileSystem.delete(path)
        fileSystem.createDirectories(path.parent!!)
        fileSystem.openReadWrite(path).use { fileHandle ->
            when (val result = finalize(password, fileHandle.sink())) {
                is ExportResult.Failure.IOError -> BackupExportResult.Failure.IOError(result.message)
                is ExportResult.Failure.ZipError -> BackupExportResult.Failure.ZipError(result.message)
                ExportResult.Success -> BackupExportResult.Success(path.toString())
            }
        }
    } catch (io: Throwable) {
        BackupExportResult.Failure.IOError(io.message ?: "Unknown IO error.")
    } finally {
        fileSystem.deleteRecursively(workDirectoryPath)
    }
}
