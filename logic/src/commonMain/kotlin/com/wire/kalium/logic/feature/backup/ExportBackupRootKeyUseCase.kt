/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.backup

import com.wire.backup.rootkey.BackupRootKeyExportData
import com.wire.backup.rootkey.BackupRootKeyExportEncryptor
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.CancellationException
import okio.Path
import okio.buffer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

public interface ExportBackupRootKeyUseCase {
    public suspend operator fun invoke(password: String): ExportBackupRootKeyResult
}

public sealed interface ExportBackupRootKeyResult {
    public data class Success(val exportFilePath: Path, val fileName: String) : ExportBackupRootKeyResult

    public sealed interface Failure : ExportBackupRootKeyResult {
        public data object NoBackupRootKey : Failure
        public data object BlankPassword : Failure
        public data class StorageFailure(val cause: Throwable) : Failure
        public data class EncryptionFailure(val cause: Throwable) : Failure
    }
}

@OptIn(ExperimentalEncodingApi::class)
internal class ExportBackupRootKeyUseCaseImpl(
    private val selfUserId: UserId,
    private val backupRootKeyRepository: BackupRootKeyRepository,
    private val kaliumFileSystem: KaliumFileSystem,
    private val encryptor: BackupRootKeyExportEncryptor = BackupRootKeyExportEncryptor,
) : ExportBackupRootKeyUseCase {

    override suspend fun invoke(password: String): ExportBackupRootKeyResult {
        if (password.isBlank()) return ExportBackupRootKeyResult.Failure.BlankPassword

        val backupRootKey = try {
            backupRootKeyRepository.getBackupRootKey()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kaliumLogger.e("Failed to read Backup Root Key for export", e)
            return ExportBackupRootKeyResult.Failure.StorageFailure(e)
        } ?: return ExportBackupRootKeyResult.Failure.NoBackupRootKey

        val envelope = try {
            encryptor.encrypt(backupRootKey.toExportData(selfUserId), password)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kaliumLogger.e("Failed to encrypt Backup Root Key export", e)
            return ExportBackupRootKeyResult.Failure.EncryptionFailure(e)
        }

        val fileName = "wire-backup-root-key-${backupRootKey.id}.wbrk"
        val exportFilePath = kaliumFileSystem.tempFilePath(fileName)

        return try {
            kaliumFileSystem.sink(exportFilePath).buffer().use { sink ->
                sink.writeUtf8(encryptor.encodeEnvelope(envelope))
            }
            ExportBackupRootKeyResult.Success(exportFilePath, fileName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kaliumLogger.e("Failed to write Backup Root Key export", e)
            ExportBackupRootKeyResult.Failure.StorageFailure(e)
        }
    }

    private fun BackupRootKey.toExportData(userId: UserId): BackupRootKeyExportData =
        BackupRootKeyExportData(
            userId = userId.toString(),
            rootKeyId = id,
            rootKeyVersion = version,
            rootKeyFingerprint = fingerprint(),
            createdAt = createdAt.toString(),
            createdByClientId = createdByClientId.value,
            keyMaterial = Base64.encode(keyMaterial),
        )
}
