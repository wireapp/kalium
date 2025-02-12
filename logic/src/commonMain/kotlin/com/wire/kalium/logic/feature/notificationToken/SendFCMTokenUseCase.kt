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
package com.wire.kalium.logic.feature.notificationToken

import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.nullableFold

/**
 * Sends to the API locally stored FCM push token
 */
interface SendFCMTokenUseCase {
    suspend operator fun invoke(): Either<SendFCMTokenError, Unit>
}

data class SendFCMTokenError(
    val status: Reason,
    val error: String? = null,
) {
    enum class Reason {
        CANT_GET_CLIENT_ID, CANT_GET_NOTIFICATION_TOKEN, CANT_REGISTER_TOKEN,
    }
}

class SendFCMTokenToAPIUseCaseImpl internal constructor(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val clientRepository: ClientRepository,
    private val notificationTokenRepository: NotificationTokenRepository,
) : SendFCMTokenUseCase {

    override suspend fun invoke(): Either<SendFCMTokenError, Unit> {
        val clientIdResult = currentClientIdProvider()
        val notificationTokenResult = notificationTokenRepository.getNotificationToken()

        val error: SendFCMTokenError? = clientIdResult.nullableFold(
            { SendFCMTokenError(SendFCMTokenError.Reason.CANT_GET_CLIENT_ID, it.toString()) },
            {
                notificationTokenResult.nullableFold(
                    {
                        SendFCMTokenError(
                            SendFCMTokenError.Reason.CANT_GET_NOTIFICATION_TOKEN,
                            it.toString()
                        )
                    },
                    { null }
                )
            }
        )

        if (error != null) {
            return Either.Left(error)
        }

        val clientId = clientIdResult.getOrNull()!!.value
        val notificationToken = notificationTokenResult.getOrNull()!!

        return clientRepository.registerToken(
            senderId = notificationToken.applicationId,
            client = clientId,
            token = notificationToken.token,
            transport = notificationToken.transport
        ).fold(
            {
                Either.Left(
                    SendFCMTokenError(
                        SendFCMTokenError.Reason.CANT_REGISTER_TOKEN,
                        it.toString()
                    )
                )
            },
            { Either.Right(Unit) }
        )
    }
}
