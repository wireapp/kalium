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

import com.wire.backup.file.cryptography.BackupPassphrase
import com.wire.backup.file.cryptography.ChaCha20Decryptor
import com.wire.kalium.protobuf.backup.BackupData
import okio.Source
import pbandk.decodeFromByteArray

abstract class CommonMPBackupImporter(selfUserDomain: String) {
    private val mapper = MPBackupMapper(selfUserDomain)

    /**
     * Attempts to import a backup, decrypting if needed.
     * @param backupBytes the raw content of the backup file
     * @param password the password used to decrypt the file. Can be `null`.
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun importBackup(data: Source, password: BackupPassphrase?): BackupImportResult = try {
        ChaCha20Decryptor.decryptBackupFile(data, password, password?.password, )
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
     *
     */
    suspend fun isBackupValid(backupBytes: ByteArray): BackupVerificationResult {

    }
}

expect class MPBackupImporter : CommonMPBackupImporter
