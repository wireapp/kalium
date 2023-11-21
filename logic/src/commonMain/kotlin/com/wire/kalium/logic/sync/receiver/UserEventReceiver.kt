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
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandler
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

internal interface UserEventReceiver : EventReceiver<Event.User>

@Suppress("LongParameterList")
internal class UserEventReceiverImpl internal constructor(
    private val clientRepository: ClientRepository,
    private val connectionRepository: ConnectionRepository,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val logout: LogoutUseCase,
    private val oneOnOneResolver: OneOnOneResolver,
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val newGroupConversationSystemMessagesCreator: Lazy<NewGroupConversationSystemMessagesCreator>,
    private val legalHoldRequestHandler: LegalHoldRequestHandler,
    private val legalHoldHandler: LegalHoldHandler
) : UserEventReceiver {

    override suspend fun onEvent(event: Event.User): Either<CoreFailure, Unit> {
        return when (event) {
            is Event.User.NewConnection -> handleNewConnection(event)
            is Event.User.ClientRemove -> handleClientRemove(event)
            is Event.User.UserDelete -> handleUserDelete(event)
            is Event.User.Update -> handleUserUpdate(event)
            is Event.User.NewClient -> handleNewClient(event)
            is Event.User.LegalHoldRequest -> legalHoldRequestHandler.handle(event)
            is Event.User.LegalHoldEnabled -> legalHoldHandler.handleEnable(event)
            is Event.User.LegalHoldDisabled -> legalHoldHandler.handleDisable(event)
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
                        if (it is StorageFailure.DataNotFound) EventLoggingStatus.SKIPPED else EventLoggingStatus.FAILURE,
                        event,
                        Pair("errorInfo", "$it")
                    )
            }
            .flatMapLeft {
                if (it is StorageFailure.DataNotFound) {
                    // not found in the local db, so this user is not in our team, not our contact nor a member of any of our groups,
                    // so we can safely ignore this event failure
                    Either.Right(Unit)
                } else {
                    Either.Left(it)
                }
            }

    private suspend fun handleNewConnection(event: Event.User.NewConnection): Either<CoreFailure, Unit> =
        userRepository.fetchUserInfo(event.connection.qualifiedToId)
            .flatMap {
                connectionRepository.insertConnectionFromEvent(event)
                    .flatMap {
                        if (event.connection.status != ConnectionState.ACCEPTED) {
                            return@flatMap Either.Right(Unit)
                        }

                        oneOnOneResolver.scheduleResolveOneOnOneConversationWithUserId(
                            event.connection.qualifiedToId,
                            delay = if (event.live) 3.seconds else ZERO
                        )
                        Either.Right(Unit)
                    }.flatMap {
                        newGroupConversationSystemMessagesCreator.value.conversationStartedUnverifiedWarning(
                            event.connection.qualifiedConversationId
                        )
                    }
            }
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
