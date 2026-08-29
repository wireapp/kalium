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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
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
import kotlin.test.assertSame

class MessageDeletionPersistenceImplTest {

    @Test
    fun givenStoredRegularMessage_whenLoading_thenStoredIdsAndSenderAreMapped() = runTest {
        val (dao, persistence) = arrangement()
        everySuspend { dao.getMessageById(eq(incomingMessageId), eq(incomingConversationEntity)) } returns regularEntity()

        assertEquals(
            Either.Right(
                MessageDeletionSnapshot(
                    messageId = storedMessageId,
                    conversationId = storedConversationId,
                    senderUserId = senderUserId,
                    isRegularEphemeral = false,
                    remoteAssetId = null,
                )
            ),
            persistence.loadMessageDeletionSnapshot(incomingConversationId, incomingMessageId),
        )
    }

    @Test
    fun givenRegularMessageWithExpiration_whenLoading_thenItIsEphemeral() = runTest {
        val (dao, persistence) = arrangement()
        everySuspend { dao.getMessageById(eq(incomingMessageId), eq(incomingConversationEntity)) } returns
                regularEntity(expireAfterMs = 1L)

        val result = persistence.loadMessageDeletionSnapshot(incomingConversationId, incomingMessageId)

        assertEquals(true, (result as Either.Right).value.isRegularEphemeral)
    }

    @Test
    fun givenNonRegularMessageWithExpiration_whenLoading_thenItIsNotEphemeral() = runTest {
        val (dao, persistence) = arrangement()
        everySuspend { dao.getMessageById(eq(incomingMessageId), eq(incomingConversationEntity)) } returns
                systemEntity(expireAfterMs = 1L)

        val result = persistence.loadMessageDeletionSnapshot(incomingConversationId, incomingMessageId)

        assertEquals(false, (result as Either.Right).value.isRegularEphemeral)
    }

    @Test
    fun givenRegularAssetMessage_whenLoading_thenRemoteAssetIdIsMapped() = runTest {
        val (dao, persistence) = arrangement()
        everySuspend { dao.getMessageById(eq(incomingMessageId), eq(incomingConversationEntity)) } returns
                regularEntity(content = assetContent)

        val result = persistence.loadMessageDeletionSnapshot(incomingConversationId, incomingMessageId)

        assertEquals(remoteAssetId, (result as Either.Right).value.remoteAssetId)
    }

    @Test
    fun givenNonAssetMessage_whenLoading_thenRemoteAssetIdIsAbsent() = runTest {
        val (dao, persistence) = arrangement()
        everySuspend { dao.getMessageById(eq(incomingMessageId), eq(incomingConversationEntity)) } returns regularEntity()

        val result = persistence.loadMessageDeletionSnapshot(incomingConversationId, incomingMessageId)

        assertEquals(null, (result as Either.Right).value.remoteAssetId)
    }

    @Test
    fun givenMissingMessage_whenLoading_thenDataNotFoundIsReturned() = runTest {
        val (dao, persistence) = arrangement()
        everySuspend { dao.getMessageById(eq(incomingMessageId), eq(incomingConversationEntity)) } returns null

        assertEquals(
            Either.Left(StorageFailure.DataNotFound),
            persistence.loadMessageDeletionSnapshot(incomingConversationId, incomingMessageId),
        )
    }

    @Test
    fun givenDaoException_whenLoading_thenExceptionIsWrapped() = runTest {
        val expected = IllegalStateException("message lookup failed")
        val (dao, persistence) = arrangement()
        everySuspend { dao.getMessageById(eq(incomingMessageId), eq(incomingConversationEntity)) } throws expected

        assertEquals(
            Either.Left(StorageFailure.Generic(expected)),
            persistence.loadMessageDeletionSnapshot(incomingConversationId, incomingMessageId),
        )
    }

    @Test
    fun givenDeleteRequest_whenHardDeleting_thenMessageIdAndMappedConversationIdAreForwarded() = runTest {
        val (dao, persistence) = arrangement()

        assertEquals(Either.Right(Unit), persistence.deleteMessage(incomingMessageId, incomingConversationId))
        verifySuspend(VerifyMode.exactly(1)) {
            dao.deleteMessage(eq(incomingMessageId), eq(incomingConversationEntity))
        }
    }

    @Test
    fun givenTombstoneRequest_whenMarkingDeleted_thenMessageIdAndMappedConversationIdAreForwarded() = runTest {
        val (dao, persistence) = arrangement()

        assertEquals(Either.Right(Unit), persistence.markMessageAsDeleted(incomingMessageId, incomingConversationId))
        verifySuspend(VerifyMode.exactly(1)) {
            dao.markMessageAsDeleted(eq(incomingMessageId), eq(incomingConversationEntity))
        }
    }

    @Test
    fun givenDaoException_whenHardDeleting_thenExceptionIsWrapped() = runTest {
        val expected = IllegalStateException("message deletion failed")
        val (dao, persistence) = arrangement()
        everySuspend { dao.deleteMessage(eq(incomingMessageId), eq(incomingConversationEntity)) } throws expected

        assertEquals(Either.Left(StorageFailure.Generic(expected)), persistence.deleteMessage(incomingMessageId, incomingConversationId))
    }

    @Test
    fun givenDaoException_whenMarkingDeleted_thenExceptionIsWrapped() = runTest {
        val expected = IllegalStateException("message tombstone failed")
        val (dao, persistence) = arrangement()
        everySuspend { dao.markMessageAsDeleted(eq(incomingMessageId), eq(incomingConversationEntity)) } throws expected

        assertEquals(
            Either.Left(StorageFailure.Generic(expected)),
            persistence.markMessageAsDeleted(incomingMessageId, incomingConversationId),
        )
    }

    @Test
    fun givenDaoCancellation_whenLoading_thenCancellationEscapes() = runTest {
        val expected = CancellationException("message lookup cancelled")
        val (dao, persistence) = arrangement()
        everySuspend { dao.getMessageById(eq(incomingMessageId), eq(incomingConversationEntity)) } throws expected

        val actual = assertFailsWith<CancellationException> {
            persistence.loadMessageDeletionSnapshot(incomingConversationId, incomingMessageId)
        }

        assertSame(expected, actual)
    }

    @Test
    fun givenDaoCancellation_whenHardDeleting_thenCancellationEscapes() = runTest {
        val expected = CancellationException("message deletion cancelled")
        val (dao, persistence) = arrangement()
        everySuspend { dao.deleteMessage(eq(incomingMessageId), eq(incomingConversationEntity)) } throws expected

        val actual = assertFailsWith<CancellationException> {
            persistence.deleteMessage(incomingMessageId, incomingConversationId)
        }

        assertSame(expected, actual)
    }

    @Test
    fun givenDaoCancellation_whenMarkingDeleted_thenCancellationEscapes() = runTest {
        val expected = CancellationException("message tombstone cancelled")
        val (dao, persistence) = arrangement()
        everySuspend { dao.markMessageAsDeleted(eq(incomingMessageId), eq(incomingConversationEntity)) } throws expected

        val actual = assertFailsWith<CancellationException> {
            persistence.markMessageAsDeleted(incomingMessageId, incomingConversationId)
        }

        assertSame(expected, actual)
    }

    private fun arrangement(): Pair<MessageDAO, IncomingMessageDeletionPersistence> {
        val dao = mock<MessageDAO>(mode = MockMode.autoUnit)
        return dao to MessageDeletionPersistenceImpl(dao)
    }

    private companion object {
        const val incomingMessageId = "incoming-message-id"
        const val storedMessageId = "stored-message-id"
        const val remoteAssetId = "remote-asset-id"
        val incomingConversationId = ConversationId("incoming-conversation", "incoming.example")
        val incomingConversationEntity = QualifiedIDEntity("incoming-conversation", "incoming.example")
        val storedConversationId = ConversationId("stored-conversation", "stored.example")
        val storedConversationEntity = QualifiedIDEntity("stored-conversation", "stored.example")
        val senderUserId = UserId("sender", "sender.example")
        val senderEntity = QualifiedIDEntity("sender", "sender.example")
        val date = Instant.parse("2026-08-19T10:15:30Z")
        val assetContent = MessageEntityContent.Asset(
            assetSizeInBytes = 10L,
            assetName = "asset.png",
            assetMimeType = "image/png",
            assetOtrKey = byteArrayOf(1),
            assetSha256Key = byteArrayOf(2),
            assetId = remoteAssetId,
            assetEncryptionAlgorithm = null,
        )

        fun regularEntity(
            content: MessageEntityContent.Regular = MessageEntityContent.Text("text"),
            expireAfterMs: Long? = null,
        ) = MessageEntity.Regular(
            id = storedMessageId,
            conversationId = storedConversationEntity,
            date = date,
            senderUserId = senderEntity,
            status = MessageEntity.Status.SENT,
            content = content,
            readCount = 0L,
            expireAfterMs = expireAfterMs,
            senderName = "sender",
            senderClientId = "sender-client",
            editStatus = MessageEntity.EditStatus.NotEdited,
        )

        fun systemEntity(expireAfterMs: Long?) = MessageEntity.System(
            id = storedMessageId,
            content = MessageEntityContent.MemberChange(
                memberUserIdList = listOf(UserIDEntity("member", "member.example")),
                memberChangeType = MessageEntity.MemberChangeType.REMOVED,
            ),
            conversationId = storedConversationEntity,
            date = date,
            senderUserId = senderEntity,
            status = MessageEntity.Status.SENT,
            expireAfterMs = expireAfterMs,
            selfDeletionEndDate = null,
            readCount = 0L,
            senderName = "sender",
        )
    }
}
