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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapper
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import io.mockative.Mockable
import kotlinx.coroutines.flow.first

@Mockable
interface MLSMessageCreator {

    suspend fun prepareMLSGroupAndCreateOutgoingMLSMessage(
        transactionContext: CryptoTransactionContext,
        groupId: GroupID,
        message: Message.Sendable
    ): Either<CoreFailure, MLSMessageApi.Message>

}

@Suppress("LongParameterList")
internal class MLSMessageCreatorImpl(
    private val conversationRepository: ConversationRepository,
    private val legalHoldStatusMapper: LegalHoldStatusMapper,
    private val mlsConversationRepository: MLSConversationRepository,
    private val joinExistingConversationUseCase: JoinExistingMLSConversationUseCase,
    private val selfUserId: UserId,
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(selfUserId = selfUserId),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : MLSMessageCreator {

    override suspend fun prepareMLSGroupAndCreateOutgoingMLSMessage(
        transactionContext: CryptoTransactionContext,
        groupId: GroupID,
        message: Message.Sendable
    ): Either<CoreFailure, MLSMessageApi.Message> = transactionContext.wrapInMLSContext { mlsContext ->
        val doesConversationExist = mlsContext.conversationExists(idMapper.toCryptoModel(groupId))
        if (doesConversationExist) {
            mlsConversationRepository.commitPendingProposals(mlsContext, groupId)
        } else {
            joinExistingConversationUseCase(transactionContext, message.conversationId)
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

        val legalHoldStatus = conversationRepository.observeLegalHoldStatus(
            message.conversationId
        ).first().let {
            legalHoldStatusMapper.mapLegalHoldConversationStatus(it, message)
        }

        val content = protoContentMapper.encodeToProtobuf(
            protoContent = ProtoContent.Readable(
                messageUid = message.id,
                messageContent = message.content,
                expectsReadConfirmation = expectsReadConfirmation,
                expiresAfterMillis = message.expirationData?.expireAfter?.inWholeMilliseconds,
                legalHoldStatus = legalHoldStatus
            )
        )
        return wrapMLSRequest {
            MLSMessageApi.Message(mlsContext.encryptMessage(idMapper.toCryptoModel(groupId), content.data))
        }
    }
}
