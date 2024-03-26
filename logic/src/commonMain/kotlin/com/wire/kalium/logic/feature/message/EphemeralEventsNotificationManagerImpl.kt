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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.di.MapperProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * This singleton allow us to queue ephemeral notifications from different user flows.
 * Ideally we should have logic that allows to mark messages as notified, but this will act for cases when we need to notify the user on
 * information we have not persisted or that is not available anymore.
 */
object EphemeralEventsNotificationManagerImpl : EphemeralEventsNotificationManager {

    private val mapper by lazy { MapperProvider.localNotificationMessageMapper() }

    private val notifications = MutableSharedFlow<LocalNotification>()

    override suspend fun observeEphemeralNotifications(): Flow<LocalNotification> = notifications

    override suspend fun scheduleDeleteConversationNotification(ephemeralConversationNotification: EphemeralConversationNotification) {
        val localNotification = mapper.fromConversationEventToLocalNotification(
            ephemeralConversationNotification.conversationEvent,
            ephemeralConversationNotification.conversation,
            ephemeralConversationNotification.user
        )
        notifications.emit(localNotification)
    }
    override suspend fun scheduleDeleteMessageNotification(message: Message) {
        val localNotification = mapper.fromMessageToMessageDeletedLocalNotification(message)
        notifications.emit(localNotification)
    }

    override suspend fun scheduleEditMessageNotification(message: Message, messageContent: MessageContent.TextEdited) {
        val localNotification = mapper.fromMessageToMessageEditedLocalNotification(message, messageContent)
        notifications.emit(localNotification)
    }

    override suspend fun scheduleConversationSeenNotification(conversationId: ConversationId) {
        val localNotification = mapper.toConversationSeen(conversationId)
        notifications.emit(localNotification)
    }
}

interface EphemeralEventsNotificationManager {
    suspend fun observeEphemeralNotifications(): Flow<LocalNotification>
    suspend fun scheduleDeleteConversationNotification(ephemeralConversationNotification: EphemeralConversationNotification)
    suspend fun scheduleDeleteMessageNotification(message: Message)
    suspend fun scheduleEditMessageNotification(message: Message, messageContent: MessageContent.TextEdited)
    suspend fun scheduleConversationSeenNotification(conversationId: ConversationId)
}

/**
 * Class to pass some data to this manager and later being able to map it to the correct types.
 * We can expand this class later when we have more cases for ephemeral notifications.
 */
data class EphemeralConversationNotification(
    val conversationEvent: Event.Conversation,
    val conversation: Conversation,
    val user: User?
)
