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
import com.wire.backup.ingest.MPBackupImporter
import okio.FileSystem
import okio.SYSTEM

interface MPBackupImporterProvider {
    fun provideImporter(
        pathToWorkDirectory: String,
        backupFileUnzipper: BackupFileUnzipper,
    ): MPBackupImporter

    fun providePeekImporter(): MPBackupImporter
}

internal class MPBackupImporterProviderImpl(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : MPBackupImporterProvider {

    override fun provideImporter(
        pathToWorkDirectory: String,
        backupFileUnzipper: BackupFileUnzipper,
    ) = MPBackupImporter(
        pathToWorkDirectory = pathToWorkDirectory,
        backupFileUnzipper = backupFileUnzipper,
    )

    override fun providePeekImporter() = MPBackupImporter(
        pathToWorkDirectory = "",
        backupFileUnzipper = { error("Not used for backup file verification") },
        fileSystem = fileSystem,
    )
}
