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

import com.wire.kalium.common.error.CoreFailure
import kotlinx.coroutines.CancellationException
import okio.Path
import kotlin.uuid.Uuid

public interface CreateBackupFromRootKeyUseCase {
    public suspend operator fun invoke(onProgress: (Float) -> Unit): CreateBackupFromRootKeyResult
}

public sealed interface CreateBackupFromRootKeyResult {
    public data class Success(
        public val backupFilePath: Path,
        public val backupFileName: String,
        public val backupId: String,
        public val rootKeyId: String,
        public val encryptionAlgorithm: String,
    ) : CreateBackupFromRootKeyResult

    public sealed interface Failure : CreateBackupFromRootKeyResult {
        public data class RootKeyGenerationFailed(
            public val cause: GenerateBackupRootKeyResult.Failure,
        ) : Failure

        public data class BackupCreationFailed(public val cause: CoreFailure) : Failure
        public data class Unknown(public val cause: Throwable) : Failure
    }
}

internal class CreateBackupFromRootKeyUseCaseImpl(
    private val getBackupRootKey: GetBackupRootKeyUseCase,
    private val generateBackupRootKey: GenerateBackupRootKeyUseCase,
    private val createMPBackup: CreateMPBackupUseCase,
    private val encryptionKeyDeriver: BackupEncryptionKeyDeriver = HkdfBackupEncryptionKeyDeriver,
    private val backupIdProvider: () -> String = { Uuid.random().toString() },
) : CreateBackupFromRootKeyUseCase {

    override suspend fun invoke(onProgress: (Float) -> Unit): CreateBackupFromRootKeyResult =
        try {
            val backupRootKey = when (val result = getBackupRootKey()) {
                is GetBackupRootKeyResult.Success -> result.backupRootKey ?: when (val generateResult = generateBackupRootKey()) {
                    is GenerateBackupRootKeyResult.Success -> generateResult.backupRootKey
                    is GenerateBackupRootKeyResult.Failure ->
                        return CreateBackupFromRootKeyResult.Failure.RootKeyGenerationFailed(generateResult)
                }

                is GetBackupRootKeyResult.Failure ->
                    return CreateBackupFromRootKeyResult.Failure.Unknown(result.cause)
            }
            val backupId = backupIdProvider()
            val passphrase = encryptionKeyDeriver.deriveBase64Passphrase(backupRootKey.keyMaterial, backupId)

            when (val result = createMPBackup(passphrase, onProgress)) {
                is CreateBackupResult.Success -> CreateBackupFromRootKeyResult.Success(
                    backupFilePath = result.backupFilePath,
                    backupFileName = result.backupFileName,
                    backupId = backupId,
                    rootKeyId = backupRootKey.id,
                    encryptionAlgorithm = HkdfBackupEncryptionKeyDeriver.ENCRYPTION_ALGORITHM,
                )

                is CreateBackupResult.Failure ->
                    CreateBackupFromRootKeyResult.Failure.BackupCreationFailed(result.coreFailure)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CreateBackupFromRootKeyResult.Failure.Unknown(e)
        }
}
