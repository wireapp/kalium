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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandler
import com.wire.kalium.logic.util.EventLoggingStatus
import com.wire.kalium.logic.util.createEventProcessingLogger
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

internal interface UserEventReceiver : EventReceiver<Event.User>

@Suppress("LongParameterList")
internal class UserEventReceiverImpl internal constructor(
    private val clientRepository: ClientRepository,
    private val connectionRepository: ConnectionRepository,
    private val userRepository: UserRepository,
    private val logout: LogoutUseCase,
    private val oneOnOneResolver: OneOnOneResolver,
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val newGroupConversationSystemMessagesCreator: Lazy<NewGroupConversationSystemMessagesCreator>,
    private val legalHoldRequestHandler: LegalHoldRequestHandler,
    private val legalHoldHandler: LegalHoldHandler
) : UserEventReceiver {

    override suspend fun onEvent(event: Event.User, deliveryInfo: EventDeliveryInfo): Either<CoreFailure, Unit> {
        return when (event) {
            is Event.User.NewConnection -> handleNewConnection(event, deliveryInfo)
            is Event.User.ClientRemove -> handleClientRemove(event)
            is Event.User.UserDelete -> handleUserDelete(event)
            is Event.User.Update -> handleUserUpdate(event)
            is Event.User.NewClient -> handleNewClient(event)
            is Event.User.LegalHoldRequest -> legalHoldRequestHandler.handle(event)
            is Event.User.LegalHoldEnabled -> legalHoldHandler.handleEnable(event)
            is Event.User.LegalHoldDisabled -> legalHoldHandler.handleDisable(event)
        }
    }

    private suspend fun handleUserUpdate(event: Event.User.Update): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return userRepository.updateUserFromEvent(event)
            .onSuccess { logger.logSuccess() }
            .onFailure {
                logger.logComplete(
                    if (it is StorageFailure.DataNotFound) EventLoggingStatus.SKIPPED else EventLoggingStatus.FAILURE,
                    arrayOf("errorInfo" to it)
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
    }

    private suspend fun handleNewConnection(event: Event.User.NewConnection, deliveryInfo: EventDeliveryInfo): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return userRepository.fetchUserInfo(event.connection.qualifiedToId)
            .flatMap {
                val previousStatus = connectionRepository.getConnection(event.connection.qualifiedConversationId)
                    .map { it.connection.status }.getOrNull()
                connectionRepository.insertConnectionFromEvent(event)
                    .flatMap {
                        if (event.connection.status == ConnectionState.ACCEPTED) {
                            oneOnOneResolver.scheduleResolveOneOnOneConversationWithUserId(
                                event.connection.qualifiedToId,
                                delay = if (deliveryInfo.source == EventSource.LIVE) 3.seconds else ZERO
                            )
                            if (previousStatus != ConnectionState.MISSING_LEGALHOLD_CONSENT) {
                                newGroupConversationSystemMessagesCreator.value.conversationStartedUnverifiedWarning(
                                    event.connection.qualifiedConversationId
                                )
                            } else Either.Right(Unit)
                        } else {
                            Either.Right(Unit)
                        }
                    }
                    .flatMap { legalHoldHandler.handleNewConnection(event) }
            }
            .onSuccess { logger.logSuccess() }
            .onFailure { logger.logFailure(it) }
    }

    private suspend fun handleClientRemove(event: Event.User.ClientRemove): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return currentClientIdProvider().map { currentClientId ->
            if (currentClientId == event.clientId) {
                logger.logSuccess("info" to "CURRENT_CLIENT")
                logout(LogoutReason.REMOVED_CLIENT, waitUntilCompletes = true)
            } else {
                logger.logSuccess("info" to "OTHER_CLIENT")
            }
        }
    }

    private suspend fun handleNewClient(event: Event.User.NewClient): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return clientRepository.saveNewClientEvent(event)
            .onSuccess { logger.logSuccess() }
            .onFailure { logger.logFailure(it) }
    }

    private suspend fun handleUserDelete(event: Event.User.UserDelete): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return if (selfUserId == event.userId) {
            logout(LogoutReason.DELETED_ACCOUNT, waitUntilCompletes = true)
            Either.Right(Unit)
        } else {
            userRepository.markUserAsDeletedAndRemoveFromGroupConversations(event.userId)
                .map { Unit }
                .onSuccess { logger.logSuccess() }
                .onFailure { logger.logFailure(it) }
        }
    }
}
