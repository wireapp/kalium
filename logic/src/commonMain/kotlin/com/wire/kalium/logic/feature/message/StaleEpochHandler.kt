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
package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuidFrom
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

interface StaleEpochHandler {
    suspend fun verifyEpoch(conversationId: ConversationId): Either<CoreFailure, Unit>
}

internal class StaleEpochHandlerImpl(
    private val systemMessageInserter: SystemMessageInserter,
    private val conversationRepository: ConversationRepository,
    private val eventRepository: EventRepository,
    private val joinExistingMLSConversation: JoinExistingMLSConversationUseCase
) : StaleEpochHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.MESSAGES) }
    override suspend fun verifyEpoch(conversationId: ConversationId): Either<CoreFailure, Unit> =
        eventRepository.lastProcessedEventId().flatMap { eventId ->
            Either.Right(Instant.fromEpochMilliseconds(uuidFrom(eventId).leastSignificantBits))
        }.flatMap { lastProcessedTimestamp ->
            logger.i("Verifying stale epoch")
            getUpdatedConversationProtocolInfo(conversationId).flatMap { protocol ->
                if (protocol is Conversation.ProtocolInfo.MLS) {
                    Either.Right(protocol)
                } else {
                    Either.Left(MLSFailure.ConversationDoesNotSupportMLS)
                }
            }.flatMap { protocolInfo ->
                if (lastProcessedTimestamp > (protocolInfo.epochTimestamp ?: Instant.DISTANT_FUTURE)) {
                    logger.w("Epoch stale due to missing commits, re-joining")
                    joinExistingMLSConversation(conversationId).flatMap {
                        systemMessageInserter.insertLostCommitSystemMessage(
                            conversationId,
                            Clock.System.now().toIsoDateTimeString()
                        )
                    }
                } else {
                    logger.i("Epoch stale due to unprocessed events")
                    Either.Right(Unit)
                }
            }
        }

    private suspend fun getUpdatedConversationProtocolInfo(conversationId: ConversationId): Either<CoreFailure, Conversation.ProtocolInfo> {
        return conversationRepository.fetchConversation(conversationId).flatMap {
            conversationRepository.getConversationProtocolInfo(conversationId)
        }
    }

}
