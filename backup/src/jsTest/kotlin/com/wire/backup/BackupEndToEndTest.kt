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
package com.wire.backup

import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.dump.BackupExportResult
import com.wire.backup.dump.CommonMPBackupExporter
import com.wire.backup.dump.MPBackupExporter
import com.wire.backup.ingest.BackupImportResult
import com.wire.backup.ingest.MPBackupImporter
import kotlinx.coroutines.await


actual fun endToEndTestSubjectProvider() = object : CommonBackupEndToEndTestSubjectProvider {
    override suspend fun exportImportDataTest(
        selfUserId: BackupQualifiedId,
        passphrase: String?,
        export: CommonMPBackupExporter.() -> Unit,
    ): BackupImportResult {
        val exporter = MPBackupExporter(selfUserId)
        exporter.export()
        val artifactPath = exporter.finalize(passphrase)
        val artifactData = (artifactPath.await() as BackupExportResult.Success).bytes
        val importer = MPBackupImporter()
        return importer.importFromFileData(artifactData, passphrase).await()
    }
}
