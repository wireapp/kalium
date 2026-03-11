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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mockable

/**
 * Sets the last device ID used for crypto state backup on the remote server.
 * This is used to track which device last uploaded the crypto state.
 */
@Mockable
public interface SetLastDeviceIdUseCase {
    /**
     * Sets the last device ID.
     * @return [SetLastDeviceIdResult.Success] if the operation succeeded,
     * or [SetLastDeviceIdResult.Failure] if it failed.
     */
    public suspend operator fun invoke(): SetLastDeviceIdResult
}

internal class SetLastDeviceIdUseCaseImpl(
    private val userId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository,
) : SetLastDeviceIdUseCase {

    override suspend fun invoke(): SetLastDeviceIdResult {
        val clientId = when (val clientResult = currentClientIdProvider.invoke()) {
            is Either.Left -> {
                kaliumLogger.e("$TAG Failed to read current client id")
                return SetLastDeviceIdResult.Failure(clientResult.value)
            }
            is Either.Right -> clientResult.value
        }

        return cryptoStateBackupRemoteRepository.setLastDeviceId(
            userId = userId.toString(),
            deviceId = clientId.value
        ).fold(
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
