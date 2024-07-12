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
package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.mention.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.message.DeliveryStatusEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.reaction.ReactionsEntity
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageMapperTest {

    val arrangement = Arrangement()

    @Test
    fun givenRegularMessageEntityWithDeliveredStatus_whenMapping_thenTheMessageHasExpectedData() {
        // given / when
        val result = arrangement.withRegularMessageEntity(
            status = MessageEntity.Status.DELIVERED
        )

        // then
        assertEquals(result.status, Message.Status.Delivered)
    }

    @Test
    fun givenRegularMessageEntityWithReadStatus_whenMapping_thenTheMessageHasExpectedData() {
        // given / when
        val result = arrangement.withRegularMessageEntity(
            status = MessageEntity.Status.READ,
            readCount = 10
        )

        // then
        assertEquals(result.status, Message.Status.Read(10))
    }

    @Test
    fun givenRegularMessageWithReadStatus_whenMapping_thenTheMessageHasExpectedData() {
        // given / when
        val result = arrangement.withRegularMessage(
            status = Message.Status.Read(10)
        )

        // then
        assertEquals(result.status, MessageEntity.Status.READ)
        assertEquals(result.readCount, 10)
    }

    @Test
    fun givenRegularMessageWithDeliveredStatus_whenMapping_thenTheMessageHasExpectedData() {
        // given / when
        val result = arrangement.withRegularMessage(
            status = Message.Status.Delivered
        )

        // then
        assertEquals(result.status, MessageEntity.Status.DELIVERED)
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

    @Test
    fun givenLegalHoldForMembersEnabled_whenMappingToMessageEntityContent_thenResultShouldHaveExpectedData() {
        // given
        val memberUserIdList = listOf(UserId("value1", "domain1"), UserId("value2", "domain2"))
        val messageContent = MessageContent.LegalHold.ForMembers.Enabled(memberUserIdList)
        // when
        val messageEntityContent = messageContent.toMessageEntityContent()
        // then
        assertIs<MessageEntityContent.LegalHold>(messageEntityContent)
        assertEquals(MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS, messageEntityContent.type)
        assertContentEquals(memberUserIdList.map { it.toDao() }, messageEntityContent.memberUserIdList)
    }
    @Test
    fun givenLegalHoldForMembersDisabled_whenMappingToMessageEntityContent_thenResultShouldHaveExpectedData() {
        // given
        val memberUserIdList = listOf(UserId("value1", "domain1"), UserId("value2", "domain2"))
        val messageContent = MessageContent.LegalHold.ForMembers.Disabled(memberUserIdList)
        // when
        val messageEntityContent = messageContent.toMessageEntityContent()
        // then
        assertIs<MessageEntityContent.LegalHold>(messageEntityContent)
        assertEquals(MessageEntity.LegalHoldType.DISABLED_FOR_MEMBERS, messageEntityContent.type)
        assertContentEquals(memberUserIdList.map { it.toDao() }, messageEntityContent.memberUserIdList)
    }
    @Test
    fun givenLegalHoldForConversationDisabled_whenMappingToMessageEntityContent_thenResultShouldHaveExpectedData() {
        // given
        val messageContent = MessageContent.LegalHold.ForConversation.Disabled
        // when
        val messageEntityContent = messageContent.toMessageEntityContent()
        // then
        assertIs<MessageEntityContent.LegalHold>(messageEntityContent)
        assertEquals(MessageEntity.LegalHoldType.DISABLED_FOR_CONVERSATION, messageEntityContent.type)
        assertContentEquals(emptyList(), messageEntityContent.memberUserIdList)
    }
    @Test
    fun givenLegalHoldForConversationEnabled_whenMappingToMessageEntityContent_thenResultShouldHaveExpectedData() {
        // given
        val messageContent = MessageContent.LegalHold.ForConversation.Enabled
        // when
        val messageEntityContent = messageContent.toMessageEntityContent()
        // then
        assertIs<MessageEntityContent.LegalHold>(messageEntityContent)
        assertEquals(MessageEntity.LegalHoldType.ENABLED_FOR_CONVERSATION, messageEntityContent.type)
        assertContentEquals(emptyList(), messageEntityContent.memberUserIdList)
    }
    @Test
    fun givenLegalHoldContentWithTypeEnabledForMembers_whenMappingToMessageContent_thenResultShouldHaveExpectedData() {
        // given
        val memberUserIdList = listOf(QualifiedIDEntity("value1", "domain1"), QualifiedIDEntity("value2", "domain2"))
        val messageEntityContent = MessageEntityContent.LegalHold(memberUserIdList, MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS)
        // when
        val messageContent = messageEntityContent.toMessageContent()
        // then
        assertIs<MessageContent.LegalHold.ForMembers.Enabled>(messageContent)
        assertContentEquals(memberUserIdList.map { it.toModel() }, messageContent.members)
    }
    @Test
    fun givenLegalHoldContentWithTypeDisabledForMembers_whenMappingToMessageContent_thenResultShouldHaveExpectedData() {
        // given
        val memberUserIdList = listOf(QualifiedIDEntity("value1", "domain1"), QualifiedIDEntity("value2", "domain2"))
        val messageEntityContent = MessageEntityContent.LegalHold(memberUserIdList, MessageEntity.LegalHoldType.DISABLED_FOR_MEMBERS)
        // when
        val messageContent = messageEntityContent.toMessageContent()
        // then
        assertIs<MessageContent.LegalHold.ForMembers.Disabled>(messageContent)
        assertContentEquals(memberUserIdList.map { it.toModel() }, messageContent.members)
    }
    @Test
    fun givenLegalHoldContentWithTypeDisabledForConversation_whenMappingToMessageContent_thenResultShouldHaveExpectedData() {
        // given
        val messageEntityContent = MessageEntityContent.LegalHold(emptyList(), MessageEntity.LegalHoldType.DISABLED_FOR_CONVERSATION)
        // when
        val messageContent = messageEntityContent.toMessageContent()
        // then
        assertIs<MessageContent.LegalHold.ForConversation.Disabled>(messageContent)
    }
    @Test
    fun givenLegalHoldContentWithTypeEnabledForConversation_whenMappingToMessageContent_thenResultShouldHaveExpectedData() {
        // given
        val messageEntityContent = MessageEntityContent.LegalHold(emptyList(), MessageEntity.LegalHoldType.ENABLED_FOR_CONVERSATION)
        // when
        val messageContent = messageEntityContent.toMessageContent()
        // then
        assertIs<MessageContent.LegalHold.ForConversation.Enabled>(messageContent)
    }

    class Arrangement {

        val messageMapper = MessageMapperImpl(UserId(value = "someValue", "someDomain"))

        @Suppress("LongParameterList")
        fun withRegularMessageEntity(
            id: String = "someId",
            conversationId: QualifiedIDEntity = QualifiedIDEntity("someId", "someDomain"),
            date: Instant = Instant.DISTANT_PAST,
            senderUserId: QualifiedIDEntity = QualifiedIDEntity("someId", "someDomain"),
            status: MessageEntity.Status = MessageEntity.Status.DELIVERED,
            visibility: MessageEntity.Visibility = MessageEntity.Visibility.VISIBLE,
            content: MessageEntityContent.Regular = MessageEntityContent.Text("someText"),
            isSelfMessage: Boolean = false,
            readCount: Long = 0,
            expireAfterMs: Long? = null,
            selfDeletionEndDate: Instant? = null,
            senderName: String? = null,
            senderClientId: String = "someId",
            editStatus: MessageEntity.EditStatus = MessageEntity.EditStatus.NotEdited,
            reactions: ReactionsEntity = ReactionsEntity.EMPTY,
            expectsReadConfirmation: Boolean = false,
            deliveryStatus: DeliveryStatusEntity = DeliveryStatusEntity.CompleteDelivery,
            sender : UserDetailsEntity? = null
        ): Message.Standalone {
            return messageMapper.fromEntityToMessage(
                MessageEntity.Regular(
                    id,
                    conversationId,
                    date,
                    senderUserId,
                    status,
                    visibility,
                    content,
                    isSelfMessage,
                    readCount,
                    expireAfterMs,
                    selfDeletionEndDate,
                    sender,
                    senderName,
                    senderClientId,
                    editStatus,
                    reactions,
                    expectsReadConfirmation,
                    deliveryStatus
                )
            )
        }

        @Suppress("LongParameterList")
        fun withRegularMessage(
            id: String = "someId",
            content: MessageContent.Regular = MessageContent.Text("someText"),
            conversationId: ConversationId = ConversationId("someValue", "someDomain"),
            date: Instant = Instant.DISTANT_PAST,
            senderUserId: UserId = UserId(value = "someValue", "someDomain"),
            status: Message.Status = Message.Status.Sent,
            visibility: Message.Visibility = Message.Visibility.VISIBLE,
            senderUserName: String? = null,
            isSelfMessage: Boolean = false,
            senderClientId: ClientId = ClientId("someValue"),
            editStatus: Message.EditStatus = Message.EditStatus.NotEdited,
            expirationData: Message.ExpirationData? = null,
            reactions: Message.Reactions = Message.Reactions.EMPTY,
            expectsReadConfirmation: Boolean = false,
            deliveryStatus: DeliveryStatus = DeliveryStatus.CompleteDelivery
        ): MessageEntity {
            return messageMapper.fromMessageToEntity(
                message = Message.Regular(
                    id = id,
                    content = content,
                    conversationId = conversationId,
                    date = date,
                    senderUserId = senderUserId,
                    status = status,
                    visibility = visibility,
                    senderUserName = senderUserName,
                    isSelfMessage = isSelfMessage,
                    senderClientId = senderClientId,
                    editStatus = editStatus,
                    expirationData = expirationData,
                    reactions = reactions,
                    expectsReadConfirmation = expectsReadConfirmation,
                    deliveryStatus = deliveryStatus
                )
            )
        }
    }
}
