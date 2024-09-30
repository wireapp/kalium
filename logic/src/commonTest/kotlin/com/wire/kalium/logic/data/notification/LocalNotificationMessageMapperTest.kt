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

import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.NotificationMessageEntity
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalNotificationMessageMapperTest {
    private lateinit var notificationMapper: LocalNotificationMessageMapper

    @BeforeTest
    fun setup() {
        notificationMapper = LocalNotificationMessageMapperImpl()
    }

    @Test
    fun givenListOfNotificationMessageEntity_whenDegradedConversationNotNotified_thenReplayDisabled() {
        val result = notificationMapper.fromEntitiesToLocalNotifications(
            listOf(
                NOTIFICATION_ENTITY.copy(degradedConversationNotified = false),
            ),
            10
        ) { _ -> TEXT_MASSAGE_NOTIFICATION }

        assertEquals(false, result[0].isReplyAllowed)
    }

    @Test
    fun givenListOfNotificationMessageEntity_whenLegalHoldDisabled_thenReplayAllowed() {
        val result = notificationMapper.fromEntitiesToLocalNotifications(
            listOf(
                NOTIFICATION_ENTITY.copy(
                    legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
                    legalHoldStatusChangeNotified = false
                )
            ),
            10
        ) { _ -> TEXT_MASSAGE_NOTIFICATION }

        assertEquals(true, result[0].isReplyAllowed)
    }

    @Test
    fun givenListOfNotificationMessageEntity_whenLegalHoldEnabled_thenReplayDisabled() {
        val result = notificationMapper.fromEntitiesToLocalNotifications(
            listOf(
                NOTIFICATION_ENTITY.copy(
                    legalHoldStatus = ConversationEntity.LegalHoldStatus.ENABLED,
                    legalHoldStatusChangeNotified = false
                )
            ),
            10
        ) { _ -> TEXT_MASSAGE_NOTIFICATION }

        assertEquals(false, result[0].isReplyAllowed)
    }

    @Test
    fun givenListOfNotificationMessageEntity_whenLegalHoldEnabledAndUserNotified_thenReplayAllowed() {
        val result = notificationMapper.fromEntitiesToLocalNotifications(
            listOf(
                NOTIFICATION_ENTITY.copy(
                    legalHoldStatus = ConversationEntity.LegalHoldStatus.ENABLED,
                    legalHoldStatusChangeNotified = true
                )
            ),
            10
        ) { _ -> TEXT_MASSAGE_NOTIFICATION }

        assertEquals(true, result[0].isReplyAllowed)
    }

    @Test
    fun givenListOfNotificationMessageEntity_whenMessagePerConvSmaller_thenOlderMessagesFilteredOut() {
        val result = notificationMapper.fromEntitiesToLocalNotifications(
            listOf(
                NOTIFICATION_ENTITY.copy(
                    id = "0",
                    legalHoldStatus = ConversationEntity.LegalHoldStatus.ENABLED,
                    legalHoldStatusChangeNotified = true
                ),
                NOTIFICATION_ENTITY.copy(
                    id = "1",
                    legalHoldStatus = ConversationEntity.LegalHoldStatus.ENABLED,
                    legalHoldStatusChangeNotified = true
                ),
                NOTIFICATION_ENTITY.copy(
                    id = "2",
                    legalHoldStatus = ConversationEntity.LegalHoldStatus.ENABLED,
                    legalHoldStatusChangeNotified = true
                ),
                NOTIFICATION_ENTITY.copy(
                    id = "3",
                    legalHoldStatus = ConversationEntity.LegalHoldStatus.ENABLED,
                    legalHoldStatusChangeNotified = true
                )
            ),
            2
        ) { _ -> TEXT_MASSAGE_NOTIFICATION }

        assertEquals(2, result[0].messages.size)
    }

    companion object {
        private val TEXT_MASSAGE_NOTIFICATION = LocalNotificationMessage.Text(
            messageId = "messageId",
            author = LocalNotificationMessageAuthor("authorName", null),
            text = "text of message",
            time = Instant.DISTANT_PAST,
            isQuotingSelfUser = false
        )

        private val NOTIFICATION_ENTITY = NotificationMessageEntity(
            id = "message_id",
            contentType = MessageEntity.ContentType.TEXT,
            isSelfDelete = false,
            senderUserId = TestUser.ENTITY_ID,
            senderImage = null,
            date = Instant.DISTANT_PAST,
            senderName = "Sender",
            text = "Some text in message",
            assetMimeType = null,
            isQuotingSelf = false,
            conversationId = TestConversation.ENTITY_ID,
            conversationName = null,
            mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
            conversationType = ConversationEntity.Type.ONE_ON_ONE,
            degradedConversationNotified = true,
            legalHoldStatus = ConversationEntity.LegalHoldStatus.ENABLED,
            legalHoldStatusChangeNotified = true
        )
    }

}
