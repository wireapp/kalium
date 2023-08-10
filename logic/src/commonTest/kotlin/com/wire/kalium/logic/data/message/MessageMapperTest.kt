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
package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.mention.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageMapperTest {

    @Test
    fun givenMessageStatus_whenMappingToMessageEntityStatus_thenMessageEntityStatusShouldMatch() {
        // Given & When & Then
        assertEquals(MessageEntity.Status.PENDING, Message.Status.PENDING.toEntity(), "Status should match PENDING")
        assertEquals(MessageEntity.Status.SENT, Message.Status.SENT.toEntity(), "Status should match SENT")
        assertEquals(MessageEntity.Status.READ, Message.Status.READ.toEntity(), "Status should match READ")
        assertEquals(MessageEntity.Status.FAILED, Message.Status.FAILED.toEntity(), "Status should match FAILED")
        assertEquals(
            MessageEntity.Status.FAILED_REMOTELY,
            Message.Status.FAILED_REMOTELY.toEntity(),
            "Status should match FAILED_REMOTELY"
        )
    }

    @Test
    fun givenMessageEntityStatus_whenMappingToMessageStatus_thenMessageStatusShouldMatch() {
        // Given & When & Then
        assertEquals(Message.Status.PENDING, MessageEntity.Status.PENDING.toModel(), "Status should match PENDING")
        assertEquals(Message.Status.SENT, MessageEntity.Status.SENT.toModel(), "Status should match SENT")
        assertEquals(Message.Status.READ, MessageEntity.Status.READ.toModel(), "Status should match READ")
        assertEquals(Message.Status.FAILED, MessageEntity.Status.FAILED.toModel(), "Status should match FAILED")
        assertEquals(
            Message.Status.FAILED_REMOTELY,
            MessageEntity.Status.FAILED_REMOTELY.toModel(),
            "Status should match FAILED_REMOTELY"
        )
    }

    @Test
    fun givenMessageVisibility_whenMappingToMessageEntityVisibility_thenMessageEntityVisibilityShouldMatch() {
        // Given & When & Then
        assertEquals(
            MessageEntity.Visibility.VISIBLE,
            Message.Visibility.VISIBLE.toEntityVisibility(),
            "Visibility should match VISIBLE"
        )
        assertEquals(
            MessageEntity.Visibility.HIDDEN,
            Message.Visibility.HIDDEN.toEntityVisibility(),
            "Visibility should match HIDDEN"
        )
        assertEquals(
            MessageEntity.Visibility.DELETED,
            Message.Visibility.DELETED.toEntityVisibility(),
            "Visibility should match DELETED"
        )
    }

    @Test
    fun givenTextEntityContent_whenMappingToMessageContent_thenMessageContentShouldMatchText() {
        // Given
        val messageBody = "Heyo @John"
        val mentionList = listOf(MessageEntity.Mention(5, 5, TestUser.SELF.id.toDao()))
        val textEntityContent = MessageEntityContent.Text(
            messageBody = messageBody,
            mentions = mentionList
        )

        // When
        val messageContent = textEntityContent.toMessageContent(false, TestUser.SELF.id)

        // Then
        assertIs<MessageContent.Text>(messageContent, "Content should be of type Text")
        assertEquals(messageBody, messageContent.value, "Message body should match")
        assertEquals(mentionList.map { it.toModel(TestUser.SELF.id) }, messageContent.mentions, "Mentions should match")
    }

    @Test
    fun givenMemberChangeFederationRemoved_whenMappingToMessageEntityContent_thenMessageEntityContentShouldMatchFederationRemoved() {
        // Given
        val memberUserIdList = listOf(
            UserId("value1", "domain1"),
            UserId("value2", "domain2")
        )
        val messageContent = MessageContent.MemberChange.FederationRemoved(memberUserIdList)

        // When
        val messageEntityContent = messageContent.toMessageEntityContent()

        // Then
        assertIs<MessageEntityContent.MemberChange>(messageEntityContent, "Content should be of type MemberChange")
        assertEquals(
            MessageEntity.MemberChangeType.FEDERATION_REMOVED,
            messageEntityContent.memberChangeType,
            "Type should match FEDERATION_REMOVED"
        )
        assertEquals(
            memberUserIdList.map { it.toDao() },
            messageEntityContent.memberUserIdList,
            "Member user ID list should match"
        )
    }
}
