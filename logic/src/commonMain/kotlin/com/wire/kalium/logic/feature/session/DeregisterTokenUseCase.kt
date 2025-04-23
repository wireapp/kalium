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

package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNotFound
import io.mockative.Mockable

/**
 * This use case will deregister the current push notification token.
 */
@Mockable
interface DeregisterTokenUseCase {
    suspend operator fun invoke(): Result

    sealed class Result {
        data object Success : Result()
        sealed class Failure : Result() {
            data object NotFound : Failure()
            data class Generic(val coreFailure: CoreFailure) : Failure()
        }
    }
}

internal class DeregisterTokenUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val notificationTokenRepository: NotificationTokenRepository
) : DeregisterTokenUseCase {

    override suspend operator fun invoke(): DeregisterTokenUseCase.Result =
        notificationTokenRepository.getNotificationToken().flatMap { notiToken ->
            clientRepository.deregisterToken(notiToken.token)
        }.fold({
            if (it is NetworkFailure.ServerMiscommunication &&
                it.kaliumException is KaliumException.InvalidRequestError &&
                it.kaliumException.isNotFound()
            ) {
                DeregisterTokenUseCase.Result.Failure.NotFound
            }
            DeregisterTokenUseCase.Result.Failure.Generic(it)
        }, {
            DeregisterTokenUseCase.Result.Success
        })
}
