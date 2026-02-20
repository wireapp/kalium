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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.network.exceptions.KaliumException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
internal class PushTokenUpdater(
    private val clientRepository: ClientRepository,
    private val notificationTokenRepository: NotificationTokenRepository,
    private val pushTokenRepository: PushTokenRepository,
    private val sessionRepository: SessionRepository,
    private val userId: UserId,
) {

    suspend fun monitorTokenChanges() {
        pushTokenRepository.observeUpdateFirebaseTokenFlag()
            .filter { it }
            .flatMapLatest { clientRepository.observeCurrentClientId().distinctUntilChanged() }
            .filterNotNull()
            .map { clientId ->
                if (!shouldTryRegisteringPushToken()) {
                    kaliumLogger.i("$TAG Native push is disabled, skipping token registration")
                    pushTokenRepository.setUpdateFirebaseTokenFlag(false)
                    return@map Either.Left(NetworkFailure.FeatureNotSupported)
                }

                notificationTokenRepository.getNotificationToken()
                    .flatMap { notificationToken ->
                        clientRepository.registerToken(
                            senderId = notificationToken.applicationId,
                            client = clientId.value,
                            token = notificationToken.token,
                            transport = notificationToken.transport
                        )
                    }
                    .onFailure {
                        kaliumLogger.i(
                            "$TAG Error while registering Firebase token " +
                                    "for the client: ${
                                        clientId.toString().obfuscateId()
                                    } error: $it"
                        )
                        handleAppNotFound(clientId, it)
                    }
            }
            .collect { result ->
                if (result.isRight()) {
                    kaliumLogger.i("$TAG Firebase token registered successfully")
                    pushTokenRepository.setUpdateFirebaseTokenFlag(false)
                }
            }
    }

    private suspend fun shouldTryRegisteringPushToken(): Boolean =
        sessionRepository.isNativePushEnabledForUser(userId).getOrElse {
            kaliumLogger.i("$TAG Failed to read native push status, retrying token registration")
            true
        }

    private suspend fun handleAppNotFound(clientId: ClientId, failure: CoreFailure) {
        val networkFailure = failure as? NetworkFailure ?: return
        if (!networkFailure.isAppNotFoundError()) {
            return
        }

        kaliumLogger.i(
            "$TAG Backend doesn't support native push for client ${clientId.toString().obfuscateId()}, " +
                    "forcing persistent websocket and stopping push token retries"
        )
        sessionRepository.setNativePushEnabledForUser(userId, false).onFailure {
            kaliumLogger.i("$TAG Failed to persist native push disabled flag: $it")
        }
        pushTokenRepository.setUpdateFirebaseTokenFlag(false).onFailure {
            kaliumLogger.i("$TAG Failed to disable push token retry flag: $it")
        }
        sessionRepository.updatePersistentWebSocketStatus(userId, true).onFailure {
            kaliumLogger.i("$TAG Failed to force persistent websocket: $it")
        }
    }

    private fun NetworkFailure.isAppNotFoundError(): Boolean {
        val invalidRequestError = (this as? NetworkFailure.ServerMiscommunication)?.kaliumException as? KaliumException.InvalidRequestError
            ?: return false
        return invalidRequestError.errorResponse.code == APP_NOT_FOUND_CODE &&
                invalidRequestError.errorResponse.label == APP_NOT_FOUND_LABEL
    }

    companion object {
        private const val TAG = "PushTokenUpdater"
        private const val APP_NOT_FOUND_CODE = 404
        private const val APP_NOT_FOUND_LABEL = "app-not-found"
    }
}
