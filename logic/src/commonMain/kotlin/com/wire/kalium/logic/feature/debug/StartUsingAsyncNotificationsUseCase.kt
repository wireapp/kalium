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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.server.ServerConfig.Companion.PRODUCTION
import com.wire.kalium.logic.feature.client.UpdateSelfClientCapabilityToConsumableNotificationsUseCase
import com.wire.kalium.logic.feature.user.SelfServerConfigUseCase

/**
 * Forces the usage of async notifications system.
 * This will register the client capability to consume async notifications.
 */
interface StartUsingAsyncNotificationsUseCase {
    suspend operator fun invoke(): StartUsingAsyncNotificationsResult
}

internal class StartUsingAsyncNotificationsUseCaseImpl(
    private val serverConfig: SelfServerConfigUseCase,
    private val updateSelfClientCapabilityToConsumableNotifications: UpdateSelfClientCapabilityToConsumableNotificationsUseCase
) : StartUsingAsyncNotificationsUseCase {

    override suspend fun invoke(): StartUsingAsyncNotificationsResult {
        return when (val result = serverConfig()) {
            is SelfServerConfigUseCase.Result.Failure -> StartUsingAsyncNotificationsResult.Failure(result.cause)
            is SelfServerConfigUseCase.Result.Success -> {
                if (result.serverLinks.links == PRODUCTION) {
                    StartUsingAsyncNotificationsResult.Failure(CoreFailure.Unknown(RuntimeException("Not supported in Prod")))
                } else {
                    when (val updatedResult = updateSelfClientCapabilityToConsumableNotifications()) {
                        is Either.Left -> StartUsingAsyncNotificationsResult.Failure(updatedResult.value)
                        is Either.Right -> StartUsingAsyncNotificationsResult.Success
                    }
                }
            }
        }
    }
}

sealed class StartUsingAsyncNotificationsResult {
    data object Success : StartUsingAsyncNotificationsResult()
    data class Failure(val coreFailure: CoreFailure) : StartUsingAsyncNotificationsResult()
}
