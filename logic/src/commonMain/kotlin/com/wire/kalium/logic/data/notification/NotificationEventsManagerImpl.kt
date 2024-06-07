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

package com.wire.kalium.logic.data.notification

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.di.MapperProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * This singleton allow us to queue checking for new regular notifications AND queue ephemeral notifications from different user flows.
 */
object NotificationEventsManagerImpl : NotificationEventsManager {

    private val mapper by lazy { MapperProvider.localNotificationMessageMapper() }

    private val ephemeralNotifications = MutableSharedFlow<LocalNotification>()
    private val regularNotificationChecking = MutableSharedFlow<Unit>()

    override suspend fun observeEphemeralNotifications(): Flow<LocalNotification> = ephemeralNotifications

    override suspend fun scheduleDeleteConversationNotification(ephemeralConversationNotification: EphemeralConversationNotification) {
        val localNotification = mapper.fromConversationEventToLocalNotification(
            ephemeralConversationNotification.conversationEvent,
            ephemeralConversationNotification.conversation,
            ephemeralConversationNotification.user
        )
        ephemeralNotifications.emit(localNotification)
    }

    override suspend fun scheduleDeleteMessageNotification(message: Message) {
        val localNotification = mapper.fromMessageToMessageDeletedLocalNotification(message)
        ephemeralNotifications.emit(localNotification)
    }

    override suspend fun scheduleEditMessageNotification(message: Message, messageContent: MessageContent.TextEdited) {
        val localNotification = mapper.fromMessageToMessageEditedLocalNotification(message, messageContent)
        ephemeralNotifications.emit(localNotification)
    }

    override suspend fun scheduleConversationSeenNotification(conversationId: ConversationId) {
        val localNotification = mapper.toConversationSeen(conversationId)
        ephemeralNotifications.emit(localNotification)
    }

    override suspend fun scheduleRegularNotificationChecking() {
        regularNotificationChecking.emit(Unit)
    }

    override suspend fun observeRegularNotificationsChecking(): Flow<Unit> = regularNotificationChecking
}

interface NotificationEventsManager {
    /**
     * Ideally we should have logic that allows to mark messages as notified,
     * but this will act for cases when we need to notify the user on
     * information we have not persisted or that is not available anymore.
     *
     * @return [Flow] of [LocalNotification] that is not stored in DB
     * and no chance to get in any other way than just emit when it's received
     */
    suspend fun observeEphemeralNotifications(): Flow<LocalNotification>

    /**
     * Schedule the notification that some conversation was deleted
     * (if the notification about that conversation is displayed it should be hidden)
     */
    suspend fun scheduleDeleteConversationNotification(ephemeralConversationNotification: EphemeralConversationNotification)

    /**
     * Schedule the notification that some message was deleted (if the notification about that message is displayed it should be hidden)
     */
    suspend fun scheduleDeleteMessageNotification(message: Message)

    /**
     * Schedule the notification that some message was edited (if the notification about that message is displayed it should be edited)
     */
    suspend fun scheduleEditMessageNotification(message: Message, messageContent: MessageContent.TextEdited)

    /**
     * Schedule the notification that informs that some conversation been seen by self-user on another device.
     * (means that notifications about that conversation can be hidden)
     */
    suspend fun scheduleConversationSeenNotification(conversationId: ConversationId)

    /**
     * Schedule re-checking of the regular notifications - notifications that are persisted and can be got by the DB-query.
     */
    suspend fun scheduleRegularNotificationChecking()

    /**
     * @return [Flow] that emits every time when new message/event that user should be notified about came and persisted
     */
    suspend fun observeRegularNotificationsChecking(): Flow<Unit>
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
