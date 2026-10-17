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
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
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
    selfUserId: UserId,
    private val protoContentMapper: ProtoContentMapper = ProtoContentMapperImpl(selfUserId = selfUserId),
    private val idMapper: IdMapper = IdMapper()
) : MLSMessageCreator {

    override suspend fun prepareMLSGroupAndCreateOutgoingMLSMessage(
        transactionContext: CryptoTransactionContext,
        groupId: GroupID,
        message: Message.Sendable
    ): Either<CoreFailure, MLSMessageApi.Message> {
        val mlsContext = transactionContext.mls ?: return Either.Left(MLSFailure.Disabled)
        val doesConversationExist = mlsContext.conversationExists(idMapper.toCryptoModel(groupId))
        return if (doesConversationExist) {
            commitPendingProposals(mlsContext, groupId)
        } else {
            joinConversation(transactionContext, message.conversationId)
        }.flatMap {
            createOutgoingMLSMessage(
                mlsContext = mlsContext,
                groupId = groupId,
                message = message
            )
        }
    }

    private suspend fun createOutgoingMLSMessage(
        mlsContext: MlsCoreCryptoContext,
        groupId: GroupID,
        message: Message.Sendable
    ): Either<CoreFailure, MLSMessageApi.Message> {
        kaliumLogger.i("Creating outgoing MLS message (groupID = ${groupId.toLogString()})")
        val expectsReadConfirmation = when (message) {
            is Message.Regular -> message.expectsReadConfirmation
            else -> false
        }

        val legalHoldStatus = declaredLegalHoldStatus(legalHoldStatus(message.conversationId), message)

        val content = protoContentMapper.encodeToProtobuf(
            protoContent = ProtoContent.Readable(
                messageUid = message.id,
                messageContent = message.content,
                expectsReadConfirmation = expectsReadConfirmation,
                expiresAfterMillis = message.expirationData?.expireAfter?.inWholeMilliseconds,
                legalHoldStatus = legalHoldStatus
            )
        )
        return encrypt(mlsContext, groupId, content.data)
            .map { MLSMessageApi.Message(it) }
    }
}
