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

package com.wire.kalium.logic.sync.receiver

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

interface FederationEventReceiver : EventReceiver<Event.Federation>

class FederationEventReceiverImpl internal constructor(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : FederationEventReceiver {

    override suspend fun onEvent(event: Event.Federation): Either<CoreFailure, Unit> {
        when (event) {
            is Event.Federation.Delete -> handleDeleteEvent(event)
        }
        return Either.Right(Unit)

    }

    // TODO KBX handle all cases
    private suspend fun handleDeleteEvent(event: Event.Federation.Delete) =
        withContext(dispatchers.io) {
            conversationRepository.getConversationIdsByDomain(selfUserId.domain).map { conversationIds ->
                conversationIds.map { conversationId ->
                    event.domains.map {
                        conversationRepository.getMemberIdsByTheSameDomainInConversation(it, conversationId)
                            .onSuccess { userIds ->
                                // TODO
                            }
                    }
                }

            }
                .onSuccess {
                    kaliumLogger
                        .logEventProcessing(
                            EventLoggingStatus.SUCCESS,
                            event
                        )
                }
                .onFailure {
                    kaliumLogger
                        .logEventProcessing(
                            EventLoggingStatus.FAILURE,
                            event,
                            Pair("errorInfo", "$it")
                        )
                }
        }
}
