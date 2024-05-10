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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.EphemeralConversationNotification
import com.wire.kalium.logic.feature.message.NotificationEventsManager
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.firstOrNull

interface DeletedConversationEventHandler {
    suspend fun handle(event: Event.Conversation.DeletedConversation)
}

internal class DeletedConversationEventHandlerImpl(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val notificationEventsManager: NotificationEventsManager
) : DeletedConversationEventHandler {

    override suspend fun handle(event: Event.Conversation.DeletedConversation) {
        val conversation = conversationRepository.getConversationById(event.conversationId)
        if (conversation != null) {
            conversationRepository.deleteConversation(event.conversationId)
                .onFailure { coreFailure ->
                    kaliumLogger
                        .logEventProcessing(
                            EventLoggingStatus.FAILURE,
                            event,
                            Pair("errorInfo", "$coreFailure")
                        )
                }.onSuccess {
                    val senderUser = userRepository.observeUser(event.senderUserId).firstOrNull()
                    val dataNotification = EphemeralConversationNotification(event, conversation, senderUser)
                    notificationEventsManager.scheduleDeleteConversationNotification(dataNotification)
                    kaliumLogger
                        .logEventProcessing(
                            EventLoggingStatus.SUCCESS,
                            event
                        )
                }
        } else {
            kaliumLogger
                .logEventProcessing(
                    EventLoggingStatus.SKIPPED,
                    event,
                    Pair("info", "Conversation delete event already handled?. Conversation is null.")
                )
        }
    }
}
