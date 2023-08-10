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

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.DeliveryStatusEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.reaction.ReactionsEntity
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

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

    class Arrangement {

        private val messageMapper = MessageMapperImpl(UserId(value = "someValue", "someDomain"))

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
            selfDeletionStartDate: Instant? = null,
            senderName: String? = null,
            senderClientId: String = "someId",
            editStatus: MessageEntity.EditStatus = MessageEntity.EditStatus.NotEdited,
            reactions: ReactionsEntity = ReactionsEntity.EMPTY,
            expectsReadConfirmation: Boolean = false,
            deliveryStatus: DeliveryStatusEntity = DeliveryStatusEntity.CompleteDelivery,
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
                    selfDeletionStartDate,
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
            date: String = Instant.DISTANT_PAST.toString(),
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
