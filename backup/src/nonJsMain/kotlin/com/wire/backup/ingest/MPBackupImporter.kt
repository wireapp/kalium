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
package com.wire.backup.ingest

import com.rickclephas.kmp.nativecoroutines.NativeCoroutines
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.filesystem.EntryStorage
import com.wire.backup.filesystem.FileBasedEntryStorage
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Sink
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
public actual class MPBackupImporter(
    private val pathToWorkDirectory: String,
    private val backupFileUnzipper: BackupFileUnzipper
) : CommonMPBackupImporter() {

    init {
        pathToWorkDirectory.toPath().also {
            if (!FileSystem.SYSTEM.exists(it)) FileSystem.SYSTEM.createDirectories(it)
        }
    }

    /**
     * Peeks into the specified backup file and retrieves metadata about it.
     *
     * @param pathToBackupFile the path to the backup file to be inspected
     * @param selfUserId the identifier of the user performing the inspection
     * @return a [BackupPeekResult] that contains information about the backup,
     * such as version, encryption status, and if it was created by the same [selfUserId]
     */
    @ObjCName("peek")
    @NativeCoroutines
    public suspend fun peekBackupFile(
        pathToBackupFile: String,
        selfUserId: BackupQualifiedId
    ): BackupPeekResult = peekBackup(FileSystem.SYSTEM.source(pathToBackupFile.toPath()), selfUserId)

    /**
     * Imports a backup from the specified root path.
     *
     * @param multiplatformBackupFilePath the path to the decrypted, unzipped backup data file
     */
    @ObjCName("importFile")
    @NativeCoroutines
    public suspend fun importFromFile(
        multiplatformBackupFilePath: String,
        passphrase: String?,
    ): BackupImportResult = importBackup(FileSystem.SYSTEM.source(multiplatformBackupFilePath.toPath()), passphrase)

    private val archiveZipPath: Path
        get() = pathToWorkDirectory.toPath() / ZIP_FILE_NAME

    override fun getUnencryptedArchiveSink(): Sink {
        FileSystem.SYSTEM.delete(archiveZipPath, mustExist = false)
        return FileSystem.SYSTEM.sink(archiveZipPath)
    }

    override suspend fun unzipAllEntries(): EntryStorage {
        val unzipPath = backupFileUnzipper.unzipBackup(archiveZipPath.toString())
        return FileBasedEntryStorage(FileSystem.SYSTEM, unzipPath.toPath(), false)
    }

    private companion object {
        const val ZIP_FILE_NAME = "unencryptedArchive.zip"
    }
}
