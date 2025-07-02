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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.persistence.dao.message.MessageEntity
import io.mockative.Mockable

@Mockable
interface MessageSendFailureHandler {
    /**
     * Handle a failure when attempting to send a message
     * due to contacts and/or clients being removed from conversation and/or added to them.
     * @return Either.Left if can't recover from error
     * @return Either.Right if the error was properly handled and a new attempt at sending message can be made
     */
    suspend fun handleClientsHaveChangedFailure(
        sendFailure: ProteusSendMessageFailure,
        conversationId: ConversationId?
    ): Either<CoreFailure, Unit>

    /**
     * Handle a failure when attempting to send a message
     * update the message status to FAILED or FAILED_REMOTELY depending on the resulted failure.
     * @param failure the failure that occured
     * @param conversationId id of the conversation of the message that failed
     * @param messageId id of the message that failed
     * @param messageType type of the message that failed (for logging purposes)
     * @param scheduleResendIfNoNetwork flag determining if the app should schedule automatic resending of failed message
     */
    suspend fun handleFailureAndUpdateMessageStatus(
        failure: CoreFailure,
        conversationId: ConversationId,
        messageId: String,
        messageType: String,
        scheduleResendIfNoNetwork: Boolean = false
    )
}

@Suppress("LongParameterList")
class MessageSendFailureHandlerImpl internal constructor(
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val clientRemoteRepository: ClientRemoteRepository,
    private val messageRepository: MessageRepository,
    private val messageSendingScheduler: MessageSendingScheduler,
    private val fetchConversation: FetchConversationUseCase,
    private val clientMapper: ClientMapper = MapperProvider.clientMapper(),
) : MessageSendFailureHandler {

    override suspend fun handleClientsHaveChangedFailure(
        sendFailure: ProteusSendMessageFailure,
        conversationId: ConversationId?
    ): Either<CoreFailure, Unit> =
        handleDeletedClients(sendFailure.deletedClientsOfUsers)
            .map { usersWithNoClientsRemaining ->
                sendFailure.missingClientsOfUsers.keys + usersWithNoClientsRemaining
            }.flatMap { usersThatNeedInfoRefresh ->
                syncUserIds(usersThatNeedInfoRefresh)
            }.flatMap {
                updateConversationInfo(sendFailure, conversationId)
            }.flatMap {
                addMissingClients(sendFailure.missingClientsOfUsers)
            }

    private suspend fun updateConversationInfo(
        sendFailure: ProteusSendMessageFailure,
        conversationId: ConversationId?
    ): Either<CoreFailure, Unit> = when {
            (conversationId == null) -> Either.Right(Unit)
            (sendFailure.deletedClientsOfUsers.isEmpty() && sendFailure.missingClientsOfUsers.isEmpty()) -> Either.Right(Unit)
            else -> fetchConversation(conversationId)
        }

    private suspend fun handleDeletedClients(deletedClient: Map<UserId, List<ClientId>>): Either<StorageFailure, Set<UserId>> {
        return if (deletedClient.isEmpty()) Either.Right(emptySet())
        else clientRepository.removeClientsAndReturnUsersWithNoClients(deletedClient).map { it.toSet() }
    }

    private suspend fun syncUserIds(userId: Set<UserId>): Either<CoreFailure, Unit> {
        return if (userId.isEmpty()) Either.Right(Unit)
        else userRepository.fetchUsersByIds(userId).map { }
    }

    private suspend fun addMissingClients(missingClients: Map<UserId, List<ClientId>>): Either<CoreFailure, Unit> =
        if (missingClients.isEmpty()) Either.Right(Unit)
        else clientRemoteRepository.fetchOtherUserClients(missingClients.keys.toList())
            .flatMap {
                it.map { (userId, clientList) -> clientMapper.toInsertClientParam(clientList, userId) }
                    .flatten().let { insertClientParamList ->
                        if (insertClientParamList.isEmpty()) Either.Right(Unit)
                        else clientRepository.storeUserClientListAndRemoveRedundantClients(insertClientParamList)
                    }
            }

    override suspend fun handleFailureAndUpdateMessageStatus(
        failure: CoreFailure,
        conversationId: ConversationId,
        messageId: String,
        messageType: String,
        scheduleResendIfNoNetwork: Boolean
    ) {
        when {
            failure is NetworkFailure.FederatedBackendFailure -> {
                kaliumLogger.e("Sending message of type $messageType failed due to federation context availability.")
                messageRepository.updateMessageStatus(MessageEntity.Status.FAILED_REMOTELY, conversationId, messageId)
            }

            failure is NetworkFailure.NoNetworkConnection && scheduleResendIfNoNetwork -> {
                kaliumLogger.i("Scheduling message for retrying in the future.")
                messageSendingScheduler.scheduleSendingOfPendingMessages()
            }

            else -> {
                messageRepository.updateMessageStatus(MessageEntity.Status.FAILED, conversationId, messageId)
            }
        }
        if (failure is CoreFailure.Unknown) {
            kaliumLogger.e(
                "There was an unknown error trying to send the message of type: $messageType, cause: $failure",
                failure.rootCause
            )
        } else {
            kaliumLogger.e("There was an error trying to send the message of type: $messageType, cause: $failure")
        }
    }
}
