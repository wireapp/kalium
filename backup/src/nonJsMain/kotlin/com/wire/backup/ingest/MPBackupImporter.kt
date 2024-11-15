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

import com.wire.kalium.protobuf.backup.BackupData
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import pbandk.decodeFromByteArray
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.ShouldRefineInSwift

class MPBackupImporter(selfUserDomain: String) {
    private val mapper = MPBackupMapper(selfUserDomain)

    /**
     * Imports backup data from a byte array and attempts to map it to a BackupModel.
     *
     * @param data The byte array containing the backup data to be imported.
     * @return A BackupImportResult indicating success or failure. On success, the parsed
     * BackupData is included; on failure, a ParsingFailure is returned.
     */
    @OptIn(ExperimentalStdlibApi::class, ExperimentalObjCRefinement::class)
    @ShouldRefineInSwift
    // TODO: Create common importer to share code with MPBackupImporter in jsMain
    fun import(data: ByteArray): BackupImportResult = try {
        println("!!!BACKUP: ${data.toHexString()}")
        BackupImportResult.Success(
            mapper.fromProtoToBackupModel(BackupData.decodeFromByteArray(data))
        )
    } catch (e: Exception) {
        e.printStackTrace()
        println(e)
        BackupImportResult.ParsingFailure
    }

    /**
     * Imports a backup from the specified root path.
     *
     * @param multiplatformBackupFilePath the path to the decrypted, unzipped backup data file
     */
    fun import(multiplatformBackupFilePath: String): BackupImportResult {
        return FileSystem.SYSTEM.read(multiplatformBackupFilePath.toPath()) {
            import(this.readByteArray())
        }
    }
}
