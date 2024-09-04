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
@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.notificationToken

import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.api.model.PushTokenBody
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
) {

    suspend fun monitorTokenChanges() {
        pushTokenRepository.observeUpdateFirebaseTokenFlag()
            .filter { it }
            .flatMapLatest { clientRepository.observeCurrentClientId().distinctUntilChanged() }
            .filterNotNull()
            .map { clientId ->
                notificationTokenRepository.getNotificationToken()
                    .flatMap { notificationToken ->
                        clientRepository.registerToken(
                            body = PushTokenBody(
                                senderId = notificationToken.applicationId,
                                client = clientId.value,
                                token = notificationToken.token,
                                transport = notificationToken.transport
                            )
                        )
                    }
                    .onFailure {
                        kaliumLogger.i(
                            "$TAG Error while registering Firebase token " +
                                    "for the client: ${clientId.toString().obfuscateId()} error: $it"
                        )
                    }
            }
            .collect { result ->
                if (result.isRight()) {
                    kaliumLogger.i("$TAG Firebase token registered successfully")
                    pushTokenRepository.setUpdateFirebaseTokenFlag(false)
                }
            }
    }

    companion object {
        private const val TAG = "PushTokenUpdater"
    }
}
