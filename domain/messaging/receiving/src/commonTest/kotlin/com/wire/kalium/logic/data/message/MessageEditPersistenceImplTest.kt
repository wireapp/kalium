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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class MessageEditPersistenceImplTest {

    @Test
    fun givenEditedTextEntity_whenLoading_thenOnlySenderTextMentionsAndEditInstantAreMapped() = runTest {
        val entity = regularMessageEntity(
            senderUserId = senderEntity,
            editStatus = MessageEntity.EditStatus.Edited(editInstant),
            content = MessageEntityContent.Text(
                messageBody = "stored text",
                linkPreview = listOf(storedLinkPreviewEntity),
                mentions = listOf(selfMentionEntity, otherMentionEntity),
                quotedMessageId = "quoted-message",
            ),
        )
        val (messageDAO, persistence) = arrangement()
        everySuspend { messageDAO.getMessageById(eq(messageId), eq(conversationEntity)) } returns entity

        val result = persistence.loadMessageEditState(conversationId, messageId)

        val state = assertIs<Either.Right<MessageEditState>>(result).value
        assertEquals(senderUserId, state.senderUserId)
        val content = assertIs<MessageEditState.Content.Text>(state.content)
        assertEquals("stored text", content.value)
        assertEquals(editInstant, content.lastEditInstant)
        assertEquals(
            listOf(
                MessageMention(0, 4, selfUserId, isSelfMention = true),
                MessageMention(5, 5, otherUserId, isSelfMention = false),
            ),
            content.mentions,
        )
    }

    @Test
    fun givenMultipartEntity_whenLoading_thenNullTextMentionsAndSupportedAttachmentsAreMappedInIndexOrder() = runTest {
        val entity = regularMessageEntity(
            senderUserId = senderEntity,
            editStatus = MessageEntity.EditStatus.NotEdited,
            content = MessageEntityContent.Multipart(
                messageBody = null,
                mentions = listOf(selfMentionEntity),
                attachments = listOf(
                    attachmentEntity("second", assetIndex = 2),
                    attachmentEntity("unsupported", assetIndex = 0, cellAsset = false),
                    attachmentEntity("first", assetIndex = 1),
                ),
            ),
        )
        val (messageDAO, persistence) = arrangement()
        everySuspend { messageDAO.getMessageById(eq(messageId), eq(conversationEntity)) } returns entity

        val result = persistence.loadMessageEditState(conversationId, messageId)

        val state = assertIs<Either.Right<MessageEditState>>(result).value
        val content = assertIs<MessageEditState.Content.Multipart>(state.content)
        assertEquals(null, content.value)
        assertEquals(null, content.lastEditInstant)
        assertEquals(listOf(MessageMention(0, 4, selfUserId, isSelfMention = true)), content.mentions)
        assertEquals(listOf("first", "second"), content.attachments.map { (it as CellAssetContent).id })
        assertEquals(AssetTransferStatus.UPLOADED, (content.attachments.first() as CellAssetContent).transferStatus)
    }

    @Test
    fun givenNonEditableRegularContent_whenLoading_thenOtherContentStateIsReturned() = runTest {
        val entity = regularMessageEntity(
            senderUserId = senderEntity,
            content = MessageEntityContent.Knock(hotKnock = true),
            editStatus = MessageEntity.EditStatus.Edited(editInstant),
        )
        val (messageDAO, persistence) = arrangement()
        everySuspend { messageDAO.getMessageById(eq(messageId), eq(conversationEntity)) } returns entity

        val result = persistence.loadMessageEditState(conversationId, messageId)

        val state = assertIs<Either.Right<MessageEditState>>(result).value
        assertEquals(MessageEditState.Content.Other, state.content)
    }

    @Test
    fun givenMissingEntity_whenLoading_thenDataNotFoundIsReturned() = runTest {
        val (messageDAO, persistence) = arrangement()
        everySuspend { messageDAO.getMessageById(eq(messageId), eq(conversationEntity)) } returns null

        assertEquals(
            Either.Left(StorageFailure.DataNotFound),
            persistence.loadMessageEditState(conversationId, messageId),
        )
    }

    @Test
    fun givenTextEdit_whenApplying_thenExactQualifiedIdsTextLinkPreviewsAndMentionsAreForwarded() = runTest {
        val textEdit = MessageContent.TextEdited(
            editMessageId = originalMessageId,
            newContent = "edited text",
            newLinkPreviews = listOf(linkPreview),
            newMentions = listOf(otherMention),
        )
        val expectedContent = MessageEntityContent.Text(
            messageBody = "edited text",
            linkPreview = listOf(
                MessageEntity.LinkPreview(
                    url = "https://wire.example/escaped\\\"value",
                    urlOffset = 7,
                    permanentUrl = "",
                    title = "Wire",
                    summary = "",
                )
            ),
            mentions = listOf(otherMentionEntity),
        )
        val (messageDAO, persistence) = arrangement()

        val result = persistence.applyTextEdit(conversationId, textEdit, messageId, editInstant)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            messageDAO.updateTextMessageContent(
                eq(editInstant),
                eq(conversationEntity),
                eq(originalMessageId),
                eq(expectedContent),
                eq(messageId),
            )
        }
    }

    @Test
    fun givenMultipartEditWithNullTextAndAttachments_whenApplying_thenEmptyTextAndMentionsAreWrittenWithoutAttachments() = runTest {
        val multipartEdit = MessageContent.MultipartEdited(
            editMessageId = originalMessageId,
            newTextContent = null,
            newMentions = listOf(otherMention),
            newAttachments = listOf(
                CellAssetContent(
                    id = "new-attachment",
                    versionId = "version",
                    mimeType = "image/png",
                    assetPath = "image.png",
                    assetSize = 10L,
                    metadata = null,
                    transferStatus = AssetTransferStatus.UPLOADED,
                )
            ),
        )
        val expectedContent = MessageEntityContent.Text(
            messageBody = "",
            mentions = listOf(otherMentionEntity),
        )
        val (messageDAO, persistence) = arrangement()

        val result = persistence.applyMultipartEdit(conversationId, multipartEdit, messageId, editInstant)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            messageDAO.updateTextMessageContent(
                eq(editInstant),
                eq(conversationEntity),
                eq(originalMessageId),
                eq(expectedContent),
                eq(messageId),
            )
        }
    }

    @Test
    fun givenResultingMessage_whenMarkingSent_thenSentStatusAndQualifiedIdsAreForwarded() = runTest {
        val (messageDAO, persistence) = arrangement()

        val result = persistence.markMessageAsSent(conversationId, messageId)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            messageDAO.updateMessageStatus(
                eq(MessageEntity.Status.SENT),
                eq(messageId),
                eq(conversationEntity),
            )
        }
    }

    @Test
    fun givenLookupThrows_whenLoading_thenFailureIsWrapped() = runTest {
        val expected = IllegalStateException("lookup failed")
        val (messageDAO, persistence) = arrangement()
        everySuspend { messageDAO.getMessageById(eq(messageId), eq(conversationEntity)) } throws expected

        assertEquals(
            Either.Left(StorageFailure.Generic(expected)),
            persistence.loadMessageEditState(conversationId, messageId),
        )
    }

    @Test
    fun givenEditWriteThrows_whenApplying_thenFailureIsWrapped() = runTest {
        val expected = IllegalStateException("edit failed")
        val (messageDAO, persistence) = arrangement()
        everySuspend {
            messageDAO.updateTextMessageContent(any(), any(), any(), any(), any())
        } throws expected

        assertEquals(
            Either.Left(StorageFailure.Generic(expected)),
            persistence.applyTextEdit(
                conversationId,
                MessageContent.TextEdited(originalMessageId, "edited"),
                messageId,
                editInstant,
            ),
        )
    }

    @Test
    fun givenStatusWriteThrows_whenMarkingSent_thenFailureIsWrapped() = runTest {
        val expected = IllegalStateException("status failed")
        val (messageDAO, persistence) = arrangement()
        everySuspend { messageDAO.updateMessageStatus(any(), any(), any()) } throws expected

        assertEquals(
            Either.Left(StorageFailure.Generic(expected)),
            persistence.markMessageAsSent(conversationId, messageId),
        )
    }

    @Test
    fun givenDaoCancellation_whenApplyingEdit_thenCancellationEscapesUnchanged() = runTest {
        val expected = CancellationException("cancelled")
        val (messageDAO, persistence) = arrangement()
        everySuspend {
            messageDAO.updateTextMessageContent(any(), any(), any(), any(), any())
        } throws expected

        val actual = assertFailsWith<CancellationException> {
            persistence.applyMultipartEdit(
                conversationId,
                MessageContent.MultipartEdited(originalMessageId, "edited"),
                messageId,
                editInstant,
            )
        }

        assertSame(expected, actual)
    }

    private fun arrangement(): Pair<MessageDAO, MessageEditPersistence> {
        val messageDAO = mock<MessageDAO>(mode = MockMode.autoUnit)
        return messageDAO to MessageEditPersistenceImpl(messageDAO, selfUserId)
    }

    private companion object {
        const val messageId = "incoming-message-id"
        const val originalMessageId = "original-message-id"
        val conversationId = ConversationId("conversation", "wire.example")
        val conversationEntity = QualifiedIDEntity("conversation", "wire.example")
        val selfUserId = UserId("self", "wire.example")
        val otherUserId = UserId("other", "wire.example")
        val senderUserId = UserId("sender", "wire.example")
        val senderEntity = QualifiedIDEntity("sender", "wire.example")
        val selfMentionEntity = MessageEntity.Mention(0, 4, QualifiedIDEntity("self", "wire.example"))
        val otherMentionEntity = MessageEntity.Mention(5, 5, QualifiedIDEntity("other", "wire.example"))
        val otherMention = MessageMention(5, 5, otherUserId, isSelfMention = false)
        val editInstant = Instant.parse("2026-08-19T10:15:30Z")
        val storedLinkPreviewEntity = MessageEntity.LinkPreview(
            url = "https://stored.example",
            urlOffset = 0,
            permanentUrl = "",
            title = "Stored",
            summary = "",
        )
        val linkPreview = MessageLinkPreview(
            url = "https://wire.example/escaped\"value",
            urlOffset = 7,
            title = "Wire",
        )

        fun regularMessageEntity(
            senderUserId: QualifiedIDEntity,
            content: MessageEntityContent.Regular,
            editStatus: MessageEntity.EditStatus,
        ) = MessageEntity.Regular(
            id = originalMessageId,
            conversationId = conversationEntity,
            date = editInstant,
            senderUserId = senderUserId,
            status = MessageEntity.Status.SENT,
            content = content,
            readCount = 0L,
            senderName = "sender",
            senderClientId = "sender-client",
            editStatus = editStatus,
        )

        fun attachmentEntity(
            id: String,
            assetIndex: Int,
            cellAsset: Boolean = true,
        ) = MessageAttachmentEntity(
            assetId = id,
            assetVersionId = "version-$id",
            cellAsset = cellAsset,
            mimeType = "image/png",
            assetPath = "$id.png",
            assetSize = 10L,
            assetWidth = 1,
            assetHeight = 2,
            assetDuration = null,
            assetTransferStatus = AssetTransferStatus.UPLOADED.name,
            assetIndex = assetIndex,
            isEditSupported = true,
        )
    }
}
