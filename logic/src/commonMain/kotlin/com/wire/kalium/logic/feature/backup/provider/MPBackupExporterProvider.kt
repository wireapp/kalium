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

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupUser
import com.wire.backup.dump.BackupExportResult
import com.wire.backup.dump.FileZipper
import com.wire.backup.dump.MPBackupExporter
import okio.FileSystem
import okio.SYSTEM

interface BackupExporter {
    fun add(user: BackupUser)
    fun add(conversation: BackupConversation)
    fun add(message: BackupMessage)
    suspend fun finalize(password: String): BackupExportResult
}

interface MPBackupExporterProvider {
    fun provideExporter(
        selfUserId: BackupQualifiedId,
        workDirectory: String,
        outputDirectory: String,
        fileZipper: FileZipper,
    ): BackupExporter
}

internal class MPBackupExporterProviderImpl(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : MPBackupExporterProvider {
    override fun provideExporter(
        selfUserId: BackupQualifiedId,
        workDirectory: String,
        outputDirectory: String,
        fileZipper: FileZipper,
    ): BackupExporter {

        val exporter = MPBackupExporter(
            selfUserId = selfUserId,
            workDirectory = workDirectory,
            outputDirectory = outputDirectory,
            fileZipper = fileZipper,
            fileSystem = fileSystem,
        )

        return object : BackupExporter {
            override fun add(user: BackupUser) {
                exporter.add(user)
            }

            override fun add(conversation: BackupConversation) {
                exporter.add(conversation)
            }

            override fun add(message: BackupMessage) {
                exporter.add(message)
            }

            override suspend fun finalize(password: String): BackupExportResult = exporter.finalize(password)
        }
    }
}
