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

import com.wire.backup.rootkey.BackupRootKeyDecryptResult
import com.wire.backup.rootkey.BackupRootKeyExportData
import com.wire.backup.rootkey.BackupRootKeyExportEncryptor
import com.wire.backup.rootkey.EncryptedBackupRootKeyEnvelope
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import okio.Path
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

public interface ImportBackupRootKeyUseCase {
    public suspend operator fun invoke(exportFilePath: Path, password: String): ImportBackupRootKeyResult
}

public sealed interface ImportBackupRootKeyResult {
    public data class Success(val backupRootKey: BackupRootKey) : ImportBackupRootKeyResult

    public sealed interface Failure : ImportBackupRootKeyResult {
        public data object BlankPassword : Failure
        public data object InvalidFile : Failure
        public data object AuthenticationFailure : Failure
        public data object UserMismatch : Failure
        public data object FingerprintMismatch : Failure
        public data class StorageFailure(val cause: Throwable) : Failure
        public data class DecryptionFailure(val cause: Throwable) : Failure
    }
}

@OptIn(ExperimentalEncodingApi::class)
internal class ImportBackupRootKeyUseCaseImpl(
    private val selfUserId: UserId,
    private val backupRootKeyRepository: BackupRootKeyRepository,
    private val kaliumFileSystem: KaliumFileSystem,
    private val encryptor: BackupRootKeyExportEncryptor = BackupRootKeyExportEncryptor,
) : ImportBackupRootKeyUseCase {

    override suspend fun invoke(exportFilePath: Path, password: String): ImportBackupRootKeyResult {
        if (password.isBlank()) return ImportBackupRootKeyResult.Failure.BlankPassword

        val envelopeJson = try {
            kaliumFileSystem.readByteArray(exportFilePath).decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kaliumLogger.e("Failed to read Backup Root Key import file", e)
            return ImportBackupRootKeyResult.Failure.StorageFailure(e)
        }

        val envelope = try {
            encryptor.decodeEnvelope(envelopeJson)
        } catch (e: Exception) {
            kaliumLogger.e("Failed to decode Backup Root Key import file", e)
            return ImportBackupRootKeyResult.Failure.InvalidFile
        }

        if (!envelope.isSupported()) {
            return ImportBackupRootKeyResult.Failure.InvalidFile
        }

        val decryptResult = try {
            encryptor.decrypt(envelope, password)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kaliumLogger.e("Failed to decrypt Backup Root Key import file", e)
            return ImportBackupRootKeyResult.Failure.DecryptionFailure(e)
        }

        val exportData = when (decryptResult) {
            is BackupRootKeyDecryptResult.Success -> decryptResult.data
            BackupRootKeyDecryptResult.AuthenticationFailure -> return ImportBackupRootKeyResult.Failure.AuthenticationFailure
            is BackupRootKeyDecryptResult.UnknownFailure -> {
                kaliumLogger.e("Failed to decrypt Backup Root Key import file: ${decryptResult.message}")
                return ImportBackupRootKeyResult.Failure.DecryptionFailure(IllegalStateException(decryptResult.message))
            }
        }

        if (exportData.userId != selfUserId.toString()) {
            return ImportBackupRootKeyResult.Failure.UserMismatch
        }

        val backupRootKey = exportData.toBackupRootKey()
            ?: return ImportBackupRootKeyResult.Failure.InvalidFile

        if (backupRootKey.fingerprint() != exportData.rootKeyFingerprint) {
            return ImportBackupRootKeyResult.Failure.FingerprintMismatch
        }

        return try {
            backupRootKeyRepository.setBackupRootKey(backupRootKey)
            ImportBackupRootKeyResult.Success(backupRootKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kaliumLogger.e("Failed to store imported Backup Root Key", e)
            ImportBackupRootKeyResult.Failure.StorageFailure(e)
        }
    }

    private fun EncryptedBackupRootKeyEnvelope.isSupported(): Boolean =
        format == BackupRootKeyExportEncryptor.FORMAT &&
                version == BackupRootKeyExportEncryptor.VERSION &&
                encryptionAlgorithm == BackupRootKeyExportEncryptor.ENCRYPTION_ALGORITHM &&
                kdf.algorithm == BackupRootKeyExportEncryptor.KDF_ALGORITHM

    private fun BackupRootKeyExportData.toBackupRootKey(): BackupRootKey? =
        try {
            val decodedKeyMaterial = Base64.decode(keyMaterial)
            if (decodedKeyMaterial.size != BACKUP_ROOT_KEY_SIZE_BYTES) return null
            BackupRootKey(
                id = rootKeyId,
                keyMaterial = decodedKeyMaterial,
                createdAt = Instant.parse(createdAt),
                createdByClientId = ClientId(createdByClientId),
                version = rootKeyVersion,
            )
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val BACKUP_ROOT_KEY_SIZE_BYTES = 32
    }
}
