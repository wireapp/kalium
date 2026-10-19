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
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi

/** Prepares this client's MLS group and encrypts a message for it. */
interface MLSMessageCreator {

    suspend fun prepareMLSGroupAndCreateOutgoingMLSMessage(
        transactionContext: CryptoTransactionContext,
        groupId: GroupID,
        message: Message.Sendable
    ): Either<CoreFailure, MLSMessageApi.Message>

    /** Like [prepareMLSGroupAndCreateOutgoingMLSMessage], but the backend doesn't persist the message. */
    suspend fun prepareMLSGroupAndCreateOutgoingTransientMLSMessage(
        transactionContext: CryptoTransactionContext,
        groupId: GroupID,
        message: Message.Sendable
    ): Either<CoreFailure, MLSMessageApi.TransientMessage>

    /**
     * Encrypts a targeted message for every client of the group selected by [target], except the sending client.
     * The TLS-serialized targeted messages are concatenated into a single batch, which is empty when [target]
     * selects no group member.
     *
     * @param transient true if the backend shouldn't persist the messages
     */
    suspend fun prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
        transactionContext: CryptoTransactionContext,
        groupId: GroupID,
        message: Message.Sendable,
        target: MessageTarget,
        transient: Boolean
    ): Either<CoreFailure, MLSMessageApi.TargetedMessageBatch>

}

/**
 * Commits pending proposals, or joins the group when this client is not in it yet, then encrypts.
 * Ports: [legalHoldStatus] reads the conversation's stored status; [commitPendingProposals] and
 * [joinConversation] may be app-only work that an extension declines with a failure.
 */
@Suppress("LongParameterList")
class MLSMessageCreatorImpl(
    private val legalHoldStatus: suspend (ConversationId) -> Either<StorageFailure, Conversation.LegalHoldStatus>,
    private val commitPendingProposals: suspend (MlsCoreCryptoContext, GroupID) -> Either<CoreFailure, Unit>,
    private val joinConversation: suspend (CryptoTransactionContext, ConversationId) -> Either<CoreFailure, Unit>,
    private val encrypt: suspend (MlsCoreCryptoContext, GroupID, ByteArray) -> Either<CoreFailure, ByteArray>,
    private val encryptTransient: suspend (MlsCoreCryptoContext, GroupID, ByteArray) -> Either<CoreFailure, ByteArray>,
    /** Encrypts for one recipient client; the last argument is true for a transient message. */
    private val encryptTargeted:
    suspend (MlsCoreCryptoContext, GroupID, CryptoQualifiedClientId, ByteArray, Boolean) -> Either<CoreFailure, ByteArray>,
    selfUserId: UserId,
    private val protoContentMapper: ProtoContentMapper = ProtoContentMapperImpl(selfUserId = selfUserId),
    private val idMapper: IdMapper = IdMapper()
) : MLSMessageCreator {

    override suspend fun prepareMLSGroupAndCreateOutgoingMLSMessage(
        transactionContext: CryptoTransactionContext,
        groupId: GroupID,
        message: Message.Sendable
    ): Either<CoreFailure, MLSMessageApi.Message> = prepareMLSGroup(transactionContext, groupId, message) { mlsContext ->
        kaliumLogger.i("Creating outgoing MLS message (groupID = ${groupId.toLogString()})")
        encrypt(mlsContext, groupId, encodeContent(message))
            .map { MLSMessageApi.Message(it) }
    }

    override suspend fun prepareMLSGroupAndCreateOutgoingTransientMLSMessage(
        transactionContext: CryptoTransactionContext,
        groupId: GroupID,
        message: Message.Sendable
    ): Either<CoreFailure, MLSMessageApi.TransientMessage> = prepareMLSGroup(transactionContext, groupId, message) { mlsContext ->
        kaliumLogger.i("Creating outgoing transient MLS message (groupID = ${groupId.toLogString()})")
        encryptTransient(mlsContext, groupId, encodeContent(message))
            .map { MLSMessageApi.TransientMessage(it) }
    }

    override suspend fun prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
        transactionContext: CryptoTransactionContext,
        groupId: GroupID,
        message: Message.Sendable,
        target: MessageTarget,
        transient: Boolean
    ): Either<CoreFailure, MLSMessageApi.TargetedMessageBatch> = prepareMLSGroup(transactionContext, groupId, message) { mlsContext ->
        createTargetedBatch(mlsContext, groupId, message, target, transient)
            .map { MLSMessageApi.TargetedMessageBatch(it) }
    }

    private suspend fun createTargetedBatch(
        mlsContext: MlsCoreCryptoContext,
        groupId: GroupID,
        message: Message.Sendable,
        target: MessageTarget,
        transient: Boolean
    ): Either<CoreFailure, ByteArray> {
        val recipients = mlsContext.members(idMapper.toCryptoModel(groupId))
            .filter { member -> !member.isSender(message) && target.selects(member) }
        kaliumLogger.i(
            "Creating outgoing targeted MLS message (transient = $transient) for ${recipients.size} clients " +
                "(groupID = ${groupId.toLogString()})"
        )
        val content = encodeContent(message)
        return recipients.foldToEitherWhileRight(ByteArray(0)) { recipient, batch ->
            encryptTargeted(mlsContext, groupId, recipient, content, transient).map { batch + it }
        }
    }

    private suspend fun <T> prepareMLSGroup(
        transactionContext: CryptoTransactionContext,
        groupId: GroupID,
        message: Message.Sendable,
        create: suspend (MlsCoreCryptoContext) -> Either<CoreFailure, T>
    ): Either<CoreFailure, T> {
        val mlsContext = transactionContext.mls ?: return Either.Left(MLSFailure.Disabled)
        val doesConversationExist = mlsContext.conversationExists(idMapper.toCryptoModel(groupId))
        return if (doesConversationExist) {
            commitPendingProposals(mlsContext, groupId)
        } else {
            joinConversation(transactionContext, message.conversationId)
        }.flatMap { create(mlsContext) }
    }

    private suspend fun encodeContent(message: Message.Sendable): ByteArray {
        val expectsReadConfirmation = when (message) {
            is Message.Regular -> message.expectsReadConfirmation
            else -> false
        }

        val legalHoldStatus = declaredLegalHoldStatus(legalHoldStatus(message.conversationId), message)

        return protoContentMapper.encodeToProtobuf(
            protoContent = ProtoContent.Readable(
                messageUid = message.id,
                messageContent = message.content,
                expectsReadConfirmation = expectsReadConfirmation,
                expiresAfterMillis = message.expirationData?.expireAfter?.inWholeMilliseconds,
                legalHoldStatus = legalHoldStatus
            )
        ).data
    }

    private fun CryptoQualifiedClientId.isSender(message: Message.Sendable): Boolean =
        isClientOf(message.senderUserId) && value == message.senderClientId.value

    private fun CryptoQualifiedClientId.isClientOf(user: UserId): Boolean =
        userId.value == user.value && userId.domain == user.domain

    private fun MessageTarget.selects(member: CryptoQualifiedClientId): Boolean = when (this) {
        is MessageTarget.Client -> recipients.any { recipient ->
            member.isClientOf(recipient.id) && recipient.clients.any { it.value == member.value }
        }
        is MessageTarget.Users -> userId.any { member.isClientOf(it) }
        is MessageTarget.Conversation -> usersToIgnore.none { member.isClientOf(it) }
    }
}
