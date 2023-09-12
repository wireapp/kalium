/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

internal interface UserEventReceiver : EventReceiver<Event.User>

@Suppress("LongParameterList")
internal class UserEventReceiverImpl internal constructor(
    private val clientRepository: ClientRepository,
    private val connectionRepository: ConnectionRepository,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val logout: LogoutUseCase,
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
) : UserEventReceiver {

    override suspend fun onEvent(event: Event.User): Either<CoreFailure, Unit> {
        return when (event) {
            is Event.User.NewConnection -> handleNewConnection(event)
            is Event.User.ClientRemove -> handleClientRemove(event)
            is Event.User.UserDelete -> handleUserDelete(event)
            is Event.User.Update -> handleUserUpdate(event)
            is Event.User.NewClient -> handleNewClient(event)
        }
    }

    private suspend fun handleUserUpdate(event: Event.User.Update) =
        userRepository.updateUserFromEvent(event)
            .onSuccess {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            }
            .onFailure {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.FAILURE,
                        event,
                        Pair("errorInfo", "$it")
                    )
            }

    private suspend fun handleNewConnection(event: Event.User.NewConnection): Either<CoreFailure, Unit> =
        connectionRepository.insertConnectionFromEvent(event)
            .onSuccess {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            }
            .onFailure {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.FAILURE,
                        event,
                        Pair("errorInfo", "$it")
                    )
            }

    private suspend fun handleClientRemove(event: Event.User.ClientRemove): Either<CoreFailure, Unit> =
        currentClientIdProvider().map { currentClientId ->
            if (currentClientId == event.clientId) {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event,
                        Pair("info", "CURRENT_CLIENT")
                    )
                logout(LogoutReason.REMOVED_CLIENT)
            } else {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event,
                        Pair("info", "OTHER_CLIENT")
                    )
            }
        }

    private suspend fun handleNewClient(event: Event.User.NewClient): Either<CoreFailure, Unit> =
        clientRepository.saveNewClientEvent(event)

    private suspend fun handleUserDelete(event: Event.User.UserDelete): Either<CoreFailure, Unit> =
        if (selfUserId == event.userId) {
            logout(LogoutReason.DELETED_ACCOUNT)
            Either.Right(Unit)
        } else {
            userRepository.removeUser(event.userId)
                .onSuccess {
                    conversationRepository.deleteUserFromConversations(event.userId)
                        .onSuccess {
                            kaliumLogger
                                .logEventProcessing(
                                    EventLoggingStatus.SUCCESS,
                                    event
                                )
                        }
                        .onFailure {
                            kaliumLogger
                                .logEventProcessing(
                                    EventLoggingStatus.FAILURE,
                                    event,
                                    Pair("errorInfo", "$it")
                                )
                        }

                }
                .onFailure {
                    kaliumLogger
                        .logEventProcessing(
                            EventLoggingStatus.FAILURE,
                            event,
                            Pair("errorInfo", "$it")
                        )
                }
        }
}
