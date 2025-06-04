/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.backup.provider

import com.wire.backup.ingest.BackupFileUnzipper
import com.wire.backup.ingest.BackupImportResult
import com.wire.backup.ingest.BackupPeekResult
import com.wire.backup.ingest.ImportDataPager
import com.wire.backup.ingest.ImportResultPager
import com.wire.backup.ingest.MPBackupImporter
import io.mockative.Mockable
import okio.FileSystem
import okio.SYSTEM

@Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")
@Mockable(BackupImporter::class, ImportResultPager::class, ImportDataPager::class)
interface BackupImporter {
    suspend fun peekBackupFile(pathToBackupFile: String): BackupPeekResult
    suspend fun importFromFile(multiplatformBackupFilePath: String, passphrase: String?): ImportResult
}

sealed class ImportResult {
    class Success(val pager: ImportResultPager) : ImportResult()
    sealed class Failure : ImportResult() {
        data object ParsingFailure : Failure()
        data object MissingOrWrongPassphrase : Failure()
        data class UnzippingError(val message: String) : Failure()
        data class UnknownError(val message: String) : Failure()
    }
}

private fun BackupImportResult.toImportResult() = when (this) {
    is BackupImportResult.Success -> ImportResult.Success(pager)
    is BackupImportResult.Failure -> when (this) {
        is BackupImportResult.Failure.ParsingFailure -> ImportResult.Failure.ParsingFailure
        is BackupImportResult.Failure.MissingOrWrongPassphrase -> ImportResult.Failure.MissingOrWrongPassphrase
        is BackupImportResult.Failure.UnzippingError -> ImportResult.Failure.UnzippingError(message)
        is BackupImportResult.Failure.UnknownError -> ImportResult.Failure.UnknownError(message)
    }
}

@Mockable
interface MPBackupImporterProvider {
    fun provideImporter(
        pathToWorkDirectory: String,
        backupFileUnzipper: BackupFileUnzipper,
    ): BackupImporter

    fun providePeekImporter(): BackupImporter
}

internal class MPBackupImporterProviderImpl(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : MPBackupImporterProvider {

    override fun provideImporter(
        pathToWorkDirectory: String,
        backupFileUnzipper: BackupFileUnzipper,
    ): BackupImporter {
        val importer = MPBackupImporter(
            pathToWorkDirectory = pathToWorkDirectory,
            backupFileUnzipper = backupFileUnzipper,
        )

        return object : BackupImporter {
            override suspend fun peekBackupFile(pathToBackupFile: String) = importer.peekBackupFile(pathToBackupFile)

            override suspend fun importFromFile(multiplatformBackupFilePath: String, passphrase: String?) =
                importer.importFromFile(multiplatformBackupFilePath, passphrase).toImportResult()
        }
    }

    override fun providePeekImporter(): BackupImporter {
        val importer = MPBackupImporter(
            pathToWorkDirectory = "",
            backupFileUnzipper = { error("Not used for backup file verification") },
            fileSystem = fileSystem,
        )
        return object : BackupImporter {
            override suspend fun peekBackupFile(pathToBackupFile: String) = importer.peekBackupFile(pathToBackupFile)

            override suspend fun importFromFile(multiplatformBackupFilePath: String, passphrase: String?) =
                importer.importFromFile(multiplatformBackupFilePath, passphrase).toImportResult()
        }
    }
}
