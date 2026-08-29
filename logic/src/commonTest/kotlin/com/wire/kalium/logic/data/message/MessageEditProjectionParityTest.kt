/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageEditProjectionParityTest {

    @Test
    fun givenEditedTextEntity_whenMappingBothPaths_thenOverlappingFieldsMatch() = runTest {
        val entity = messageEntity(
            MessageEntityContent.Text(
                messageBody = "stored text",
                mentions = mentionEntities,
            )
        )

        val (fullMessage, editState) = mapBoth(entity)
        val fullContent = assertIs<MessageContent.Text>(fullMessage.content)
        val editContent = assertIs<MessageEditState.Content.Text>(editState.content)

        assertEquals(fullMessage.senderUserId, editState.senderUserId)
        assertEquals(
            assertIs<Message.EditStatus.Edited>(fullMessage.editStatus).lastEditInstant,
            editContent.lastEditInstant,
        )
        assertEquals(fullContent.value, editContent.value)
        assertEquals(fullContent.mentions, editContent.mentions)
    }

    @Test
    fun givenEditedMultipartEntity_whenMappingBothPaths_thenOverlappingFieldsMatch() = runTest {
        val entity = messageEntity(
            MessageEntityContent.Multipart(
                messageBody = null,
                mentions = mentionEntities,
                attachments = listOf(
                    attachment("second", assetIndex = 2),
                    attachment("unsupported", assetIndex = 0, cellAsset = false),
                    attachment("first", assetIndex = 1),
                ),
            )
        )

        val (fullMessage, editState) = mapBoth(entity)
        val fullContent = assertIs<MessageContent.Multipart>(fullMessage.content)
        val editContent = assertIs<MessageEditState.Content.Multipart>(editState.content)

        assertEquals(fullMessage.senderUserId, editState.senderUserId)
        assertEquals(
            assertIs<Message.EditStatus.Edited>(fullMessage.editStatus).lastEditInstant,
            editContent.lastEditInstant,
        )
        assertEquals(fullContent.value, editContent.value)
        assertEquals(fullContent.mentions, editContent.mentions)
        assertEquals(fullContent.attachments, editContent.attachments)
    }

    private suspend fun mapBoth(entity: MessageEntity.Regular): Pair<Message.Regular, MessageEditState> {
        val messageDAO = mock<MessageDAO>(mode = MockMode.autoUnit) {
            everySuspend { getMessageById(eq(messageId), eq(conversationEntity)) } returns entity
        }
        val fullMessage = assertIs<Message.Regular>(MessageMapperImpl(selfUserId).fromEntityToMessage(entity))
        val editState = assertIs<Either.Right<MessageEditState>>(
            MessageEditPersistenceImpl(messageDAO, selfUserId).loadMessageEditState(conversationId, messageId)
        ).value
        return fullMessage to editState
    }

    private fun messageEntity(content: MessageEntityContent.Regular) = MessageEntity.Regular(
        id = messageId,
        conversationId = conversationEntity,
        date = messageDate,
        senderUserId = senderEntity,
        status = MessageEntity.Status.SENT,
        content = content,
        readCount = 0,
        senderName = "sender",
        senderClientId = "sender-client",
        editStatus = MessageEntity.EditStatus.Edited(editInstant),
    )

    private fun attachment(
        id: String,
        assetIndex: Int,
        cellAsset: Boolean = true,
    ) = MessageAttachmentEntity(
        assetId = id,
        assetVersionId = "version-$id",
        cellAsset = cellAsset,
        mimeType = "image/png",
        assetPath = "$id.png",
        assetSize = 10,
        assetWidth = 1,
        assetHeight = 2,
        assetDuration = null,
        assetTransferStatus = AssetTransferStatus.UPLOADED.name,
        assetIndex = assetIndex,
        isEditSupported = true,
    )

    private companion object {
        const val messageId = "message-id"
        val conversationId = ConversationId("conversation", "wire.example")
        val conversationEntity = QualifiedIDEntity("conversation", "wire.example")
        val selfUserId = UserId("self", "wire.example")
        val otherUserId = UserId("other", "wire.example")
        val senderEntity = QualifiedIDEntity("sender", "wire.example")
        val messageDate = Instant.parse("2026-08-22T10:00:00Z")
        val editInstant = Instant.parse("2026-08-22T10:15:30Z")
        val mentionEntities = listOf(
            MessageEntity.Mention(0, 4, QualifiedIDEntity(selfUserId.value, selfUserId.domain)),
            MessageEntity.Mention(5, 5, QualifiedIDEntity(otherUserId.value, otherUserId.domain)),
        )
    }
}
