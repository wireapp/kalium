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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.EventProcessingPerformanceData
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import kotlinx.datetime.Clock

internal interface TypingIndicatorHandler {
    suspend fun handle(event: Event.Conversation.TypingIndicator): Either<StorageFailure, Unit>
}

internal class TypingIndicatorHandlerImpl(
    private val selfUserId: UserId,
    private val typingIndicatorIncomingRepository: TypingIndicatorIncomingRepository
) : TypingIndicatorHandler {
    override suspend fun handle(event: Event.Conversation.TypingIndicator): Either<StorageFailure, Unit> {
        val initialTime = Clock.System.now()
        if (event.senderUserId == selfUserId) {
            kaliumLogger.withFeatureId(EVENT_RECEIVER).logEventProcessing(
                EventLoggingStatus.SKIPPED,
                event,
                "isForSelfUser" to true,
                performanceData = EventProcessingPerformanceData.TimeTaken(
                    duration = (Clock.System.now() - initialTime)
                )
            )
            return Either.Right(Unit)
        }

        when (event.typingIndicatorMode) {
            Conversation.TypingIndicatorMode.STARTED -> typingIndicatorIncomingRepository.addTypingUserInConversation(
                event.conversationId,
                event.senderUserId
            )

            Conversation.TypingIndicatorMode.STOPPED -> typingIndicatorIncomingRepository.removeTypingUserInConversation(
                event.conversationId,
                event.senderUserId
            )
        }.also {
            kaliumLogger
                .withFeatureId(EVENT_RECEIVER)
                .logEventProcessing(
                    status = EventLoggingStatus.SUCCESS,
                    event = event,
                    "isForSelfUser" to false,
                    performanceData = EventProcessingPerformanceData.TimeTaken(
                        duration = (Clock.System.now() - initialTime),
                    )
                )
        }

        return Either.Right(Unit)
    }
}
