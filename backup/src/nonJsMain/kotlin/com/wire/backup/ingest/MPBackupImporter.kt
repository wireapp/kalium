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
import com.wire.backup.data.BackupData
import com.wire.backup.filesystem.BackupPageStorage
import com.wire.backup.filesystem.FileBasedBackupPageStorage
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Sink
import okio.Source
import okio.use
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Entity able to parse backed-up data and returns
 * digestible data in [BackupData] format.
 * @sample samples.backup.BackupSamplesNonJS.peekBackup
 * @sample samples.backup.BackupSamples.commonImport
 */
@OptIn(ExperimentalObjCName::class)
public actual class MPBackupImporter : CommonMPBackupImporter {

    private val pathToWorkDirectory: String
    private val backupFileUnzipper: BackupFileUnzipper
    private val fileSystem: FileSystem

    public constructor(
        pathToWorkDirectory: String,
        backupFileUnzipper: BackupFileUnzipper,
        fileSystem: FileSystem
    ) : super() {
        this.pathToWorkDirectory = pathToWorkDirectory
        this.backupFileUnzipper = backupFileUnzipper
        this.fileSystem = fileSystem
        pathToWorkDirectory.toPath().also {
            if (!fileSystem.exists(it)) fileSystem.createDirectories(it)
        }
    }

    public constructor(
        pathToWorkDirectory: String,
        backupFileUnzipper: BackupFileUnzipper,
    ) : this(pathToWorkDirectory, backupFileUnzipper, FileSystem.SYSTEM)

    /**
     * Peeks into the specified backup file and retrieves metadata about it.
     *
     * @param pathToBackupFile the path to the backup file to be inspected
     * @return a [BackupPeekResult] that contains information about the backup,
     * such as version, encryption status, etc.
     */
    @ObjCName("peek")
    @NativeCoroutines
    public suspend fun peekBackupFile(
        pathToBackupFile: String
    ): BackupPeekResult = withBackupFile(pathToBackupFile) { peekBackup(it) }

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
    ): BackupImportResult = withBackupFile(multiplatformBackupFilePath) { importBackup(it, passphrase) }

    private val archiveZipPath: Path
        get() = pathToWorkDirectory.toPath() / ZIP_FILE_NAME

    override fun getUnencryptedArchiveSink(): Sink {
        fileSystem.delete(archiveZipPath, mustExist = false)
        return fileSystem.sink(archiveZipPath)
    }

    override suspend fun unzipAllEntries(): BackupPageStorage {
        val unzipPath = backupFileUnzipper.unzipBackup(archiveZipPath.toString())
        return FileBasedBackupPageStorage(fileSystem, unzipPath.toPath(), false)
    }

    private inline fun <T> withBackupFile(
        path: String,
        block: (Source) -> T
    ): T = fileSystem.source(path.toPath()).use { block(it) }

    private companion object {
        const val ZIP_FILE_NAME = "unencryptedArchive.zip"
    }
}
