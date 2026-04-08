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
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository

/**
 * Sets the last device ID used for crypto state backup on the remote server.
 * This is used to track which device last uploaded the crypto state.
 */
public interface SetLastDeviceIdUseCase {
    /**
     * Sets the last device ID.
     * @return [SetLastDeviceIdResult.Success] if the operation succeeded,
     * or [SetLastDeviceIdResult.Failure] if it failed.
     */
    public suspend operator fun invoke(clientId: String): SetLastDeviceIdResult
}

internal class SetLastDeviceIdUseCaseImpl(
    private val cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository,
) : SetLastDeviceIdUseCase {

    override suspend fun invoke(clientId: String): SetLastDeviceIdResult {
        return cryptoStateBackupRemoteRepository.setLastDeviceId(deviceId = clientId).fold(
            { error ->
                kaliumLogger.e("$TAG Failed to set last device id: $error")
                SetLastDeviceIdResult.Failure(error)
            },
            {
                kaliumLogger.i("$TAG Successfully set last device id")
                SetLastDeviceIdResult.Success
            }
        )
    }

    companion object {
        const val TAG = "[SetLastDeviceIdUseCase]"
    }
}

public sealed class SetLastDeviceIdResult {
    public data object Success : SetLastDeviceIdResult()
    public data class Failure(val error: CoreFailure) : SetLastDeviceIdResult()
}
