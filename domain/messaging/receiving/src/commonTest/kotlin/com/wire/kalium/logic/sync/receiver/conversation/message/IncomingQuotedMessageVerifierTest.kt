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
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IncomingQuotedMessageVerifierTest {

    private val encoder = MessageContentEncoder()

    @Test
    fun givenQuoteWithoutHash_whenHandlingText_thenQuoteIsMarkedUnverifiedWithoutLookup() = runTest {
        val quote = MessageContent.QuoteReference("quoted-message", quotedMessageSha256 = null, isVerified = true)
        val dao = mock<MessageDAO>()

        val result = IncomingQuotedMessageVerifierImpl(dao, encoder)(conversationId, quote)

        assertFalse(result.isVerified)
        verifySuspend(VerifyMode.not) {
            dao.getMessageById(eq(quotedMessageId), eq(conversationEntity))
        }
    }

    @Test
    fun givenQuoteWithMatchingHash_whenHandlingMultipart_thenQuoteIsMarkedVerified() = runTest {
        val multipartCase = supportedCases().single { it.expectedContent is MessageContent.Multipart }
        val hash = requireNotNull(encoder.encodeMessageContent(storedDate, multipartCase.expectedContent)).sha256Digest
        val quote = MessageContent.QuoteReference(quotedMessageId, hash, isVerified = false)
        val dao = mock<MessageDAO> {
            everySuspend { getMessageById(eq(quotedMessageId), eq(conversationEntity)) } returns multipartCase.entity
        }

        val result = IncomingQuotedMessageVerifierImpl(dao, encoder)(conversationId, quote)

        assertTrue(result.isVerified)
    }

    @Test
    fun givenQuotedMessageMissing_whenHandlingText_thenQuoteIsMarkedUnverified() = runTest {
        val quote = MessageContent.QuoteReference("missing-message", byteArrayOf(1, 2, 3), isVerified = true)
        val dao = mock<MessageDAO> {
            everySuspend { getMessageById(eq("missing-message"), eq(conversationEntity)) } returns null
        }

        val result = IncomingQuotedMessageVerifierImpl(dao, encoder)(conversationId, quote)

        assertFalse(result.isVerified)
    }

    @Test
    fun givenQuotedMessageHashDoesNotMatch_whenHandlingText_thenQuoteIsMarkedUnverified() = runTest {
        val textCase = supportedCases().single { it.expectedContent is MessageContent.Text }
        val quote = MessageContent.QuoteReference(quotedMessageId, byteArrayOf(9, 8, 7), isVerified = true)
        val dao = mock<MessageDAO> {
            everySuspend { getMessageById(eq(quotedMessageId), eq(conversationEntity)) } returns textCase.entity
        }

        val result = IncomingQuotedMessageVerifierImpl(dao, encoder)(conversationId, quote)

        assertFalse(result.isVerified)
    }

    @Test
    fun givenNullHash_whenVerifying_thenReferenceIsUnverifiedAndDaoLookupIsSkipped() = runTest {
        val dao = mock<MessageDAO>()
        val verifier = IncomingQuotedMessageVerifierImpl(dao, encoder)
        val reference = MessageContent.QuoteReference(quotedMessageId, null, true)

        val result = verifier(conversationId, reference)

        assertReferenceOnlyVerificationChanged(reference, result, expectedVerified = false)
        verifySuspend(VerifyMode.not) {
            dao.getMessageById(eq(quotedMessageId), eq(conversationEntity))
        }
    }

    @Test
    fun givenMatchingHash_whenVerifyingEverySupportedStoredContent_thenReferenceIsVerified() = runTest {
        supportedCases().forEach { case ->
            val expectedHash = requireNotNull(encoder.encodeMessageContent(storedDate, case.expectedContent)).sha256Digest
            val dao = mock<MessageDAO> {
                everySuspend { getMessageById(eq(quotedMessageId), eq(conversationEntity)) } returns case.entity
            }
            val reference = MessageContent.QuoteReference(quotedMessageId, expectedHash, false)

            val result = IncomingQuotedMessageVerifierImpl(dao, encoder)(conversationId, reference)

            assertReferenceOnlyVerificationChanged(reference, result, expectedVerified = true)
            verifySuspend(VerifyMode.exactly(1)) {
                dao.getMessageById(eq(quotedMessageId), eq(conversationEntity))
            }
        }
    }

    @Test
    fun givenMismatchingHash_whenVerifyingEverySupportedStoredContent_thenReferenceIsUnverified() = runTest {
        supportedCases().forEach { case ->
            val dao = mock<MessageDAO> {
                everySuspend { getMessageById(eq(quotedMessageId), eq(conversationEntity)) } returns case.entity
            }
            val reference = MessageContent.QuoteReference(quotedMessageId, byteArrayOf(9, 8, 7), true)

            val result = IncomingQuotedMessageVerifierImpl(dao, encoder)(conversationId, reference)

            assertReferenceOnlyVerificationChanged(reference, result, expectedVerified = false)
        }
    }

    @Test
    fun givenMultipartAttachmentsOutOfOrderAndUnsupported_whenVerifying_thenSortedFilteredUuidsAreHashed() = runTest {
        val multipartCase = supportedCases().single { it.expectedContent is MessageContent.Multipart }
        val expectedHash = requireNotNull(encoder.encodeMessageContent(storedDate, multipartCase.expectedContent)).sha256Digest
        val dao = mock<MessageDAO> {
            everySuspend { getMessageById(eq(quotedMessageId), eq(conversationEntity)) } returns multipartCase.entity
        }
        val reference = MessageContent.QuoteReference(quotedMessageId, expectedHash, false)

        val result = IncomingQuotedMessageVerifierImpl(dao, encoder)(conversationId, reference)

        assertTrue(result.isVerified)
    }

    @Test
    fun givenMissingStoredMessage_whenVerifying_thenReferenceIsUnverified() = runTest {
        val dao = mock<MessageDAO> {
            everySuspend { getMessageById(eq(quotedMessageId), eq(conversationEntity)) } returns null
        }
        val reference = MessageContent.QuoteReference(quotedMessageId, byteArrayOf(1), true)

        val result = IncomingQuotedMessageVerifierImpl(dao, encoder)(conversationId, reference)

        assertReferenceOnlyVerificationChanged(reference, result, expectedVerified = false)
    }

    @Test
    fun givenOrdinaryDaoFailure_whenVerifying_thenReferenceIsUnverified() = runTest {
        val expected = IllegalStateException("lookup failed")
        val dao = mock<MessageDAO> {
            everySuspend { getMessageById(eq(quotedMessageId), eq(conversationEntity)) } throws expected
        }
        val reference = MessageContent.QuoteReference(quotedMessageId, byteArrayOf(1), true)

        val result = IncomingQuotedMessageVerifierImpl(dao, encoder)(conversationId, reference)

        assertReferenceOnlyVerificationChanged(reference, result, expectedVerified = false)
    }

    @Test
    fun givenDaoCancellation_whenVerifying_thenSameCancellationEscapes() = runTest {
        val expected = CancellationException("lookup cancelled")
        val dao = mock<MessageDAO> {
            everySuspend { getMessageById(eq(quotedMessageId), eq(conversationEntity)) } throws expected
        }
        val verifier = IncomingQuotedMessageVerifierImpl(dao, encoder)

        val actual = assertFailsWith<CancellationException> {
            verifier(conversationId, MessageContent.QuoteReference(quotedMessageId, byteArrayOf(1), false))
        }

        assertSame(expected, actual)
    }

    @Test
    fun givenStoredContentMappingFailure_whenVerifying_thenExceptionEscapes() = runTest {
        val invalidAttachment = attachmentEntity(
            assetId = "invalid",
            assetIndex = 0,
            assetTransferStatus = "NOT_A_TRANSFER_STATUS",
        )
        val entity = regularEntity(MessageEntityContent.Multipart("body", attachments = listOf(invalidAttachment)))
        val dao = mock<MessageDAO> {
            everySuspend { getMessageById(eq(quotedMessageId), eq(conversationEntity)) } returns entity
        }
        val verifier = IncomingQuotedMessageVerifierImpl(dao, encoder)

        assertFailsWith<IllegalArgumentException> {
            verifier(conversationId, MessageContent.QuoteReference(quotedMessageId, byteArrayOf(1), false))
        }
    }

    @Test
    fun givenUnsupportedRegularOrSystemContent_whenVerifying_thenReferenceIsUnverified() = runTest {
        listOf(
            regularEntity(MessageEntityContent.Knock(false)),
            systemEntity(),
        ).forEach { entity ->
            val dao = mock<MessageDAO> {
                everySuspend { getMessageById(eq(quotedMessageId), eq(conversationEntity)) } returns entity
            }
            val reference = MessageContent.QuoteReference(quotedMessageId, byteArrayOf(1), true)

            val result = IncomingQuotedMessageVerifierImpl(dao, encoder)(conversationId, reference)

            assertFalse(result.isVerified)
        }
    }

    private fun supportedCases(): List<SupportedCase> = listOf(
        SupportedCase(
            entity = regularEntity(MessageEntityContent.Text("stored text")),
            expectedContent = MessageContent.Text("stored text"),
        ),
        SupportedCase(
            entity = regularEntity(assetEntityContent("stored-asset-id")),
            expectedContent = assetContent("stored-asset-id"),
        ),
        SupportedCase(
            entity = regularEntity(MessageEntityContent.Location(52.52f, 13.405f, "Berlin", 12)),
            expectedContent = MessageContent.Location(52.52f, 13.405f, "Berlin", 12),
        ),
        SupportedCase(
            entity = regularEntity(
                MessageEntityContent.Multipart(
                    messageBody = "stored multipart",
                    attachments = listOf(
                        attachmentEntity("filtered", assetIndex = 0, cellAsset = false),
                        attachmentEntity("second", assetIndex = 2),
                        attachmentEntity("first", assetIndex = 1),
                    ),
                )
            ),
            expectedContent = MessageContent.Multipart(
                value = "stored multipart",
                attachments = listOf(cellAttachment("first"), cellAttachment("second")),
            ),
        ),
    )

    private fun regularEntity(content: MessageEntityContent.Regular): MessageEntity.Regular = MessageEntity.Regular(
        id = quotedMessageId,
        conversationId = conversationEntity,
        date = storedDate,
        senderUserId = senderEntity,
        status = MessageEntity.Status.SENT,
        content = content,
        readCount = 0,
        senderName = null,
        senderClientId = "stored-client",
        editStatus = MessageEntity.EditStatus.NotEdited,
    )

    private fun systemEntity(): MessageEntity.System = MessageEntity.System(
        id = quotedMessageId,
        content = MessageEntityContent.MissedCall,
        conversationId = conversationEntity,
        date = storedDate,
        senderUserId = senderEntity,
        status = MessageEntity.Status.SENT,
        expireAfterMs = null,
        selfDeletionEndDate = null,
        readCount = 0,
        senderName = null,
    )

    private fun assetEntityContent(assetId: String) = MessageEntityContent.Asset(
        assetSizeInBytes = 10,
        assetName = "asset.bin",
        assetMimeType = "application/octet-stream",
        assetOtrKey = byteArrayOf(1),
        assetSha256Key = byteArrayOf(2),
        assetId = assetId,
        assetEncryptionAlgorithm = "AES_CBC",
    )

    private fun assetContent(assetId: String) = MessageContent.Asset(
        AssetContent(
            sizeInBytes = 0,
            mimeType = "",
            remoteData = AssetContent.RemoteData(
                otrKey = byteArrayOf(),
                sha256 = byteArrayOf(),
                assetId = assetId,
                assetToken = null,
                assetDomain = null,
                encryptionAlgorithm = null,
            ),
        )
    )

    private fun attachmentEntity(
        assetId: String,
        assetIndex: Int,
        cellAsset: Boolean = true,
        assetTransferStatus: String = AssetTransferStatus.NOT_DOWNLOADED.name,
    ) = MessageAttachmentEntity(
        assetId = assetId,
        cellAsset = cellAsset,
        mimeType = "application/octet-stream",
        assetPath = null,
        assetSize = null,
        assetWidth = null,
        assetHeight = null,
        assetDuration = null,
        assetTransferStatus = assetTransferStatus,
        assetIndex = assetIndex,
        isEditSupported = false,
    )

    private fun cellAttachment(assetId: String) = CellAssetContent(
        id = assetId,
        versionId = "",
        mimeType = "application/octet-stream",
        assetPath = null,
        assetSize = null,
        metadata = null,
        transferStatus = AssetTransferStatus.NOT_DOWNLOADED,
    )

    private fun assertReferenceOnlyVerificationChanged(
        expected: MessageContent.QuoteReference,
        actual: MessageContent.QuoteReference,
        expectedVerified: Boolean,
    ) {
        assertEquals(expected.quotedMessageId, actual.quotedMessageId)
        assertSame(expected.quotedMessageSha256, actual.quotedMessageSha256)
        assertContentEquals(expected.quotedMessageSha256, actual.quotedMessageSha256)
        assertEquals(expectedVerified, actual.isVerified)
    }

    private data class SupportedCase(
        val entity: MessageEntity.Regular,
        val expectedContent: MessageContent,
    )

    private companion object {
        const val quotedMessageId = "quoted-message"
        val conversationId = ConversationId("conversation", "wire.example")
        val conversationEntity = QualifiedIDEntity("conversation", "wire.example")
        val senderEntity = QualifiedIDEntity("sender", "wire.example")
        val storedDate = Instant.parse("2026-08-20T10:15:30.456Z")
    }
}
