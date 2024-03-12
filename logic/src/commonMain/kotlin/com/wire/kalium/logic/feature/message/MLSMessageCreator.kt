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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapper
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import kotlinx.coroutines.flow.first

interface MLSMessageCreator {

    suspend fun createOutgoingMLSMessage(
        groupId: GroupID,
        message: Message.Sendable
    ): Either<CoreFailure, MLSMessageApi.Message>

}

class MLSMessageCreatorImpl(
    private val conversationRepository: ConversationRepository,
    private val legalHoldStatusMapper: LegalHoldStatusMapper,
    private val mlsClientProvider: MLSClientProvider,
    private val selfUserId: UserId,
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(selfUserId = selfUserId),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : MLSMessageCreator {

    override suspend fun createOutgoingMLSMessage(groupId: GroupID, message: Message.Sendable): Either<CoreFailure, MLSMessageApi.Message> {
        return mlsClientProvider.getMLSClient().flatMap { mlsClient ->
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
            wrapMLSRequest { MLSMessageApi.Message(mlsClient.encryptMessage(idMapper.toCryptoModel(groupId), content.data)) }
        }
    }

}
