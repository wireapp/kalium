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
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger

interface MLSWrongEpochHandler {
    suspend fun onMLSWrongEpoch(
        conversationId: ConversationId,
        dateIso: String,
    )
}

internal class MLSWrongEpochHandlerImpl(
    private val selfUserId: UserId,
    private val persistMessage: PersistMessageUseCase,
    private val conversationRepository: ConversationRepository,
    private val joinExistingMLSConversation: JoinExistingMLSConversationUseCase
) : MLSWrongEpochHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun onMLSWrongEpoch(
        conversationId: ConversationId,
        dateIso: String,
    ) {
        logger.i("Handling MLS WrongEpoch result")
        conversationRepository.getConversationProtocolInfo(conversationId).flatMap { protocol ->
            if (protocol is Conversation.ProtocolInfo.MLS) {
                Either.Right(protocol)
            } else {
                Either.Left(MLSFailure.ConversationDoesNotSupportMLS)
            }
        }.flatMap { currentProtocol ->
            getUpdatedConversationEpoch(conversationId).map { updatedEpoch ->
                updatedEpoch != null && updatedEpoch != currentProtocol.epoch
            }
        }.flatMap { isRejoinNeeded ->
            if (isRejoinNeeded) {
                joinExistingMLSConversation(conversationId)
            } else Either.Right(Unit)
        }.flatMap {
            insertInfoMessage(conversationId, dateIso)
        }
    }

    private suspend fun getUpdatedConversationEpoch(conversationId: ConversationId): Either<CoreFailure, ULong?> {
        return conversationRepository.fetchConversation(conversationId).flatMap {
            conversationRepository.getConversationProtocolInfo(conversationId)
        }.map { updatedProtocol ->
            (updatedProtocol as? Conversation.ProtocolInfo.MLS)?.epoch
        }
    }

    private suspend fun insertInfoMessage(conversationId: ConversationId, dateIso: String): Either<CoreFailure, Unit> {
        val mlsEpochWarningMessage = Message.System(
            id = uuid4().toString(),
            content = MessageContent.MLSWrongEpochWarning,
            conversationId = conversationId,
            date = dateIso,
            senderUserId = selfUserId,
            status = Message.Status.Read,
            visibility = Message.Visibility.VISIBLE,
            senderUserName = null
        )
        return persistMessage(mlsEpochWarningMessage)
    }
}
