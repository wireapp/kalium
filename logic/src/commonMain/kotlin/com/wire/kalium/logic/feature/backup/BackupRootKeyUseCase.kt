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
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.util.SecureRandom
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

public interface GetBackupRootKeyUseCase {
    public suspend operator fun invoke(): GetBackupRootKeyResult
}

public interface GenerateBackupRootKeyUseCase {
    public suspend operator fun invoke(): GenerateBackupRootKeyResult
}

public sealed interface GetBackupRootKeyResult {
    public data class Success(val backupRootKey: BackupRootKey?) : GetBackupRootKeyResult
    public data class Failure(val cause: Throwable) : GetBackupRootKeyResult
}

public sealed interface GenerateBackupRootKeyResult {
    public data class Success(val backupRootKey: BackupRootKey) : GenerateBackupRootKeyResult
    public sealed interface Failure : GenerateBackupRootKeyResult {
        public data class CurrentClientIdUnavailable(val cause: CoreFailure) : Failure
        public data class StorageFailure(val cause: Throwable) : Failure
    }
}

internal class GetBackupRootKeyUseCaseImpl(
    private val backupRootKeyRepository: BackupRootKeyRepository,
) : GetBackupRootKeyUseCase {

    override suspend fun invoke(): GetBackupRootKeyResult =
        try {
            GetBackupRootKeyResult.Success(backupRootKeyRepository.getBackupRootKey())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kaliumLogger.e("Failed to get backup root key", e)
            GetBackupRootKeyResult.Failure(e)
        }
}

internal class GenerateBackupRootKeyUseCaseImpl(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val backupRootKeyRepository: BackupRootKeyRepository,
    private val secureRandom: SecureRandom = SecureRandom(),
    private val idProvider: () -> String = { Uuid.random().toString() },
) : GenerateBackupRootKeyUseCase {

    override suspend fun invoke(): GenerateBackupRootKeyResult =
        currentClientIdProvider().fold(
            { failure -> GenerateBackupRootKeyResult.Failure.CurrentClientIdUnavailable(failure) },
            { clientId ->
                try {
                    val backupRootKey = BackupRootKey(
                        id = idProvider(),
                        keyMaterial = secureRandom.nextBytes(KEY_MATERIAL_LENGTH),
                        createdAt = DateTimeUtil.currentInstant(),
                        createdByClientId = clientId,
                        version = BACKUP_ROOT_KEY_VERSION,
                    )
                    backupRootKeyRepository.setBackupRootKey(backupRootKey)
                    GenerateBackupRootKeyResult.Success(backupRootKey)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    kaliumLogger.e("Failed to generate backup root key", e)
                    GenerateBackupRootKeyResult.Failure.StorageFailure(e)
                }
            }
        )

    private companion object {
        const val KEY_MATERIAL_LENGTH = 32
        const val BACKUP_ROOT_KEY_VERSION = 1
    }
}
