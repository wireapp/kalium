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

package com.wire.kalium.messaging.sending

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.persistence.dao.message.MessageEntity

interface MessageSendFailureHandler {
    /**
     * Handle a failure when attempting to send a message
     * due to contacts and/or clients being removed from conversation and/or added to them.
     * @return Either.Left if can't recover from error
     * @return Either.Right if the error was properly handled and a new attempt at sending message can be made
     */
    suspend fun handleClientsHaveChangedFailure(
        transactionContext: CryptoTransactionContext,
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

/** Users, clients and members the sender refreshes after the backend rejects a Proteus message. */
interface ProteusRecipients {
    /** Deletes [clients] and returns the users left without clients. */
    suspend fun removeClients(clients: Map<UserId, List<ClientId>>): Either<StorageFailure, List<UserId>>

    suspend fun fetchUsers(userIds: Set<UserId>): Either<CoreFailure, Unit>

    suspend fun fetchConversation(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit>

    /** Fetches the clients of [userIds] and stores them, removing clients the backend no longer lists. */
    suspend fun fetchClients(userIds: List<UserId>): Either<CoreFailure, Unit>

    suspend fun markClientsAsValid(clients: Map<UserId, List<ClientId>>): Either<StorageFailure, Unit>
}

/**
 * Ports: [recipients] repairs the recipient list; [updateMessageStatus] records the failure;
 * [scheduleResend] asks the platform to retry pending messages once the network is back.
 */
class MessageSendFailureHandlerImpl(
    private val recipients: ProteusRecipients,
    private val updateMessageStatus: suspend (MessageEntity.Status, ConversationId, String) -> Either<CoreFailure, Unit>,
    private val scheduleResend: () -> Unit,
) : MessageSendFailureHandler {

    override suspend fun handleClientsHaveChangedFailure(
        transactionContext: CryptoTransactionContext,
        sendFailure: ProteusSendMessageFailure,
        conversationId: ConversationId?
    ): Either<CoreFailure, Unit> =
        handleDeletedClients(sendFailure.deletedClientsOfUsers)
            .map { usersWithNoClientsRemaining ->
                sendFailure.missingClientsOfUsers.keys + usersWithNoClientsRemaining
            }.flatMap { usersThatNeedInfoRefresh ->
                syncUserIds(usersThatNeedInfoRefresh)
            }.flatMap {
                updateConversationInfo(transactionContext, sendFailure, conversationId)
            }.flatMap {
                addMissingClients(sendFailure.missingClientsOfUsers)
            }

    private suspend fun updateConversationInfo(
        transactionContext: CryptoTransactionContext,
        sendFailure: ProteusSendMessageFailure,
        conversationId: ConversationId?
    ): Either<CoreFailure, Unit> = when {
            (conversationId == null) -> Either.Right(Unit)
            (sendFailure.deletedClientsOfUsers.isEmpty() && sendFailure.missingClientsOfUsers.isEmpty()) -> Either.Right(Unit)
            else -> recipients.fetchConversation(transactionContext, conversationId)
        }

    private suspend fun handleDeletedClients(deletedClient: Map<UserId, List<ClientId>>): Either<StorageFailure, Set<UserId>> {
        return if (deletedClient.isEmpty()) Either.Right(emptySet())
        else recipients.removeClients(deletedClient).map { it.toSet() }
    }

    private suspend fun syncUserIds(userId: Set<UserId>): Either<CoreFailure, Unit> {
        return if (userId.isEmpty()) Either.Right(Unit)
        else recipients.fetchUsers(userId)
    }

    private suspend fun addMissingClients(missingClients: Map<UserId, List<ClientId>>): Either<CoreFailure, Unit> =
        if (missingClients.isEmpty()) Either.Right(Unit)
        else recipients.fetchClients(missingClients.keys.toList())
            .flatMap { recipients.markClientsAsValid(missingClients) }

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
                updateMessageStatus(MessageEntity.Status.FAILED_REMOTELY, conversationId, messageId)
            }

            failure is NetworkFailure.NoNetworkConnection && scheduleResendIfNoNetwork -> {
                kaliumLogger.i("Scheduling message for retrying in the future.")
                scheduleResend()
            }

            else -> {
                updateMessageStatus(MessageEntity.Status.FAILED, conversationId, messageId)
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
