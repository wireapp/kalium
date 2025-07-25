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

import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapper
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.util.arrangement.provider.ProteusCoreCryptoContextArrangement
import com.wire.kalium.logic.util.arrangement.provider.ProteusCoreCryptoContextArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.EqualsMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageEnvelopeCreatorTest {

    @Test
    fun givenRecipients_whenCreatingAnEnvelope_thenProteusClientShouldBeUsedToEncryptForEachClient() = runTest {
        val recipients = TEST_RECIPIENTS
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map {
                CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        val encryptedData = byteArrayOf()
        val plainData = byteArrayOf(0x42, 0x73)

        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedReturning(sessionIds.associateWith { encryptedData })
            .withEncodeReadableReturning(PlainMessageBlob(plainData))
            .arrange()


        creator.createOutgoingEnvelope(arrangement.proteusContext, recipients, TestMessage.TEXT_MESSAGE)

        coVerify {
            arrangement.proteusContext.encryptBatched(
                eq(plainData),
                eq(sessionIds)
            )
        }

        coVerify {
            arrangement.conversationRepository.observeLegalHoldStatus(any())
        }.wasInvoked(once)

        verify {
            arrangement.legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenMessageContentIsTooBig_whenCreatingAnEnvelope_thenShouldCreateExternalMessageInstructions() = runTest {
        // Given
        // A big byte array as the readable content
        val plainData = ByteArray(NON_EXTERNAL_CONTENT_SIZE_LIMIT) { it.toByte() }

        val recipients = TEST_RECIPIENTS
        val externalInstructionsArray = byteArrayOf(0x42, 0x13)
        val encryptedData = byteArrayOf(0x66)
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map {
                CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        // Should only attempt to E2EE the external instructions, not the content itself
        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedReturning(sessionIds.associateWith { encryptedData })
            .withEncodeReadableReturning(PlainMessageBlob(plainData))
            .withEncodeExternalReturning(PlainMessageBlob(externalInstructionsArray))
            .arrange()

        // When
        val envelope = creator.createOutgoingEnvelope(arrangement.proteusContext, recipients, TestMessage.TEXT_MESSAGE)

        // Then
        envelope.shouldSucceed {
            assertTrue { it.dataBlob!!.data.size >= NON_EXTERNAL_CONTENT_SIZE_LIMIT }

            it.recipients.forEach { recipientEntry ->
                recipientEntry.clientPayloads.forEach { clientPayload ->
                    assertEquals(encryptedData, clientPayload.payload.data)
                }
            }
        }

        coVerify {
            arrangement.conversationRepository.observeLegalHoldStatus(any())
        }.wasInvoked(once)

        verify {
            arrangement.legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenMessageIsTooBigDueToTooManyRecipientClients_whenCreatingAnEnvelope_thenShouldCreateExternalMessageInstructions() = runTest {
        // Given
        val recipients = listOf(
            Recipient(TEST_MEMBER_1, listOf(ClientId("c1"), ClientId("c2"), ClientId("c3"))),
            Recipient(TEST_MEMBER_2, listOf(ClientId("c4"), ClientId("c5"), ClientId("c6"))),
        )
        val numberOfClients = recipients.sumOf { it.clients.size }
        // A big byte array as the readable content
        val plainData = ByteArray(NON_EXTERNAL_CONTENT_SIZE_LIMIT / numberOfClients) { it.toByte() }

        val externalInstructionsArray = byteArrayOf(0x42, 0x13)
        val encryptedData = byteArrayOf(0x66)
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map {
                CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        // Should only attempt to E2EE the external instructions, not the content itself
        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedReturning(sessionIds.associateWith { encryptedData })
            .withEncodeReadableReturning(PlainMessageBlob(plainData))
            .withEncodeExternalReturning(PlainMessageBlob(externalInstructionsArray))
            .arrange()

        // When
        val envelope = creator.createOutgoingEnvelope(arrangement.proteusContext, recipients, TestMessage.TEXT_MESSAGE)

        // Then
        envelope.shouldSucceed {
            it.recipients.forEach { recipientEntry ->
                recipientEntry.clientPayloads.forEach { clientPayload ->
                    assertEquals(encryptedData, clientPayload.payload.data)
                }
            }
        }

        coVerify {
            arrangement.conversationRepository.observeLegalHoldStatus(any())
        }.wasInvoked(once)

        verify {
            arrangement.legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenMessageContentIsSmall_whenCreatingAnEnvelope_thenShouldNotCreateExternalMessageInstructions() = runTest {
        // Given
        val plainData = ByteArray(1) { it.toByte() }

        val recipients = TEST_RECIPIENTS
        val encryptedData = byteArrayOf(0x66)
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map {
                CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        // Should only attempt to E2EE the content itself
        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedReturning(sessionIds.associateWith { encryptedData })
            .withEncodeReadableReturning(PlainMessageBlob(plainData))
            .arrange()

        // When
        val envelope = creator.createOutgoingEnvelope(arrangement.proteusContext, recipients, TestMessage.TEXT_MESSAGE)

        // Then
        envelope.shouldSucceed {
            assertNull(it.dataBlob)

            it.recipients.forEach { recipientEntry ->
                recipientEntry.clientPayloads.forEach { clientPayload ->
                    assertEquals(encryptedData, clientPayload.payload.data)
                }
            }
        }

        coVerify {
            arrangement.conversationRepository.observeLegalHoldStatus(any())
        }.wasInvoked(once)

        verify {
            arrangement.legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenEncryptionSucceeds_whenCreatingAnEnvelope_thenTheResultShouldContainAllEntries() = runTest {
        val recipients = TEST_RECIPIENTS
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map {
                CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        val encryptedData = byteArrayOf()
        val plainData = byteArrayOf(0x42, 0x73)

        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedReturning(sessionIds.associateWith { encryptedData })
            .withEncodeReadableReturning(PlainMessageBlob(plainData))
            .arrange()

        creator.createOutgoingEnvelope(arrangement.proteusContext, recipients, TestMessage.TEXT_MESSAGE)
            .shouldSucceed { envelope ->
                assertEquals(TestMessage.TEXT_MESSAGE.senderClientId, envelope.senderClientId)

                // Should get a corresponding contact for the envelope entry
                // For each recipient
                TEST_RECIPIENTS.forEach { recipient ->
                    // Should get a matching recipient entry in the created envelope
                    val matchingRecipientEntry = envelope.recipients.first { recipientEntry ->
                        recipient.id == recipientEntry.userId
                    }

                    // All clients of this contact should have a matching payload in the entry
                    recipient.clients.forEach { client ->
                        val hasFoundClientEntry = matchingRecipientEntry.clientPayloads.any { clientPayload ->
                            clientPayload.clientId == client
                        }
                        assertTrue(hasFoundClientEntry)
                    }
                }
            }

        coVerify {
            arrangement.conversationRepository.observeLegalHoldStatus(any())
        }.wasInvoked(once)

        verify {
            arrangement.legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenProteusThrowsDuringEncryption_whenCreatingEnvelope_thenTheFailureShouldBePropagated() = runTest {
        val exception = ProteusException("OOPS", ProteusException.Code.PANIC, 15)

        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedThrowing(exception)
            .withEncodeReadableReturning(PlainMessageBlob(byteArrayOf()))
            .arrange()

        creator.createOutgoingEnvelope(arrangement.proteusContext, TEST_RECIPIENTS, TestMessage.TEXT_MESSAGE)
            .shouldFail {
                assertIs<ProteusFailure>(it)
                assertEquals(exception, it.proteusException)
            }

        coVerify {
            arrangement.conversationRepository.observeLegalHoldStatus(any())
        }.wasInvoked(once)

        verify {
            arrangement.legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenProteusThrowsDuringEncryption_whenCreatingEnvelope_thenNoMoreEncryptionsShouldBeAttempted() = runTest {
        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedThrowing(ProteusException("OOPS", ProteusException.Code.PANIC, 15))
            .withEncodeReadableReturning(PlainMessageBlob(byteArrayOf()))
            .arrange()

        creator.createOutgoingEnvelope(arrangement.proteusContext, TEST_RECIPIENTS, TestMessage.TEXT_MESSAGE)

        coVerify {
            arrangement.proteusContext.encryptBatched(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationRepository.observeLegalHoldStatus(any())
        }.wasInvoked(once)

        verify {
            arrangement.legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenRecipients_whenCreatingBroadcastEnvelope_thenProteusClientShouldBeUsedToEncryptForEachClient() = runTest {
        val recipients = TEST_RECIPIENTS
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map {
                CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        val encryptedData = byteArrayOf()
        val plainData = byteArrayOf(0x42, 0x73)

        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedReturning(sessionIds.associateWith { encryptedData })
            .withEncodeReadableReturning(PlainMessageBlob(plainData))
            .arrange()

        creator.createOutgoingBroadcastEnvelope(arrangement.proteusContext, recipients, TestMessage.BROADCAST_MESSAGE)

        coVerify {
            arrangement.proteusContext.encryptBatched(
                eq(plainData),
                eq(sessionIds)
            )
        }
    }

    @Test
    fun givenMessageContentIsTooBig_whenCreatingBroadcastEnvelope_thenShouldCreateExternalMessageInstructions() = runTest {
        // Given
        // A big byte array as the readable content
        val plainData = ByteArray(NON_EXTERNAL_CONTENT_SIZE_LIMIT) { it.toByte() }

        val recipients = TEST_RECIPIENTS
        val externalInstructionsArray = byteArrayOf(0x42, 0x13)
        val encryptedData = byteArrayOf(0x66)
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map {
                CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        // Should only attempt to E2EE the external instructions, not the content itself
        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedReturning(sessionIds.associateWith { encryptedData }, EqualsMatcher(externalInstructionsArray))
            .withEncodeReadableReturning(PlainMessageBlob(plainData))
            .withEncodeExternalReturning(PlainMessageBlob(externalInstructionsArray))
            .arrange()

        // When
        val envelope = creator.createOutgoingBroadcastEnvelope(arrangement.proteusContext, recipients, TestMessage.BROADCAST_MESSAGE)

        // Then
        envelope.shouldSucceed {
            assertTrue { it.dataBlob!!.data.size >= NON_EXTERNAL_CONTENT_SIZE_LIMIT }

            it.recipients.forEach { recipientEntry ->
                recipientEntry.clientPayloads.forEach { clientPayload ->
                    assertEquals(encryptedData, clientPayload.payload.data)
                }
            }
        }
    }

    @Test
    fun givenMessageIsTooBigDueToTooManyRecipientClients_whenCreatingBroadcastEnvelope_thenShouldCreateExternalMessageInstructions() =
        runTest {
            // Given
            // A big byte array as the readable content
            val recipients = listOf(
                Recipient(TEST_MEMBER_1, listOf(ClientId("c1"), ClientId("c2"), ClientId("c3"))),
                Recipient(TEST_MEMBER_2, listOf(ClientId("c4"), ClientId("c5"), ClientId("c6"))),
            )
            val numberOfClients = recipients.sumOf { it.clients.size }
            val plainData = ByteArray(NON_EXTERNAL_CONTENT_SIZE_LIMIT / numberOfClients) { it.toByte() }

            val externalInstructionsArray = byteArrayOf(0x42, 0x13)
            val encryptedData = byteArrayOf(0x66)
            val sessionIds = recipients.flatMap { recipient ->
                recipient.clients.map {
                    CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
                }
            }

            // Should only attempt to E2EE the external instructions, not the content itself
            val (arrangement, creator) = Arrangement()
                .withEncryptBatchedReturning(sessionIds.associateWith { encryptedData }, EqualsMatcher(externalInstructionsArray))
                .withEncodeReadableReturning(PlainMessageBlob(plainData))
                .withEncodeExternalReturning(PlainMessageBlob(externalInstructionsArray))
                .arrange()

            // When
            val envelope = creator.createOutgoingBroadcastEnvelope(arrangement.proteusContext, recipients, TestMessage.BROADCAST_MESSAGE)

            // Then
            envelope.shouldSucceed {
                it.recipients.forEach { recipientEntry ->
                    recipientEntry.clientPayloads.forEach { clientPayload ->
                        assertEquals(encryptedData, clientPayload.payload.data)
                    }
                }
            }
        }

    @Test
    fun givenMessageContentIsSmall_whenCreatingBroadcastEnvelope_thenShouldNotCreateExternalMessageInstructions() = runTest {
        // Given
        val plainData = ByteArray(1) { it.toByte() }

        val recipients = TEST_RECIPIENTS
        val encryptedData = byteArrayOf(0x66)
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map {
                CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        // Should only attempt to E2EE the content itself
        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedReturning(sessionIds.associateWith { encryptedData }, EqualsMatcher(plainData))
            .withEncodeReadableReturning(PlainMessageBlob(plainData))
            .arrange()

        // When
        val envelope = creator.createOutgoingBroadcastEnvelope(arrangement.proteusContext, recipients, TestMessage.BROADCAST_MESSAGE)

        // Then
        envelope.shouldSucceed {
            assertNull(it.dataBlob)

            it.recipients.forEach { recipientEntry ->
                recipientEntry.clientPayloads.forEach { clientPayload ->
                    assertEquals(encryptedData, clientPayload.payload.data)
                }
            }
        }
    }

    @Test
    fun givenEncryptionSucceeds_whenCreatingBroadcastEnvelope_thenTheResultShouldContainAllEntries() = runTest {
        // Given
        val recipients = TEST_RECIPIENTS
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map {
                CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        val encryptedData = byteArrayOf()
        val plainData = byteArrayOf(0x42, 0x73)

        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedReturning(sessionIds.associateWith { encryptedData })
            .withEncodeReadableReturning(PlainMessageBlob(plainData))
            .arrange()

        // When
        creator.createOutgoingBroadcastEnvelope(arrangement.proteusContext, recipients, TestMessage.BROADCAST_MESSAGE)
            // Then
            .shouldSucceed { envelope ->
                assertEquals(TestMessage.TEXT_MESSAGE.senderClientId, envelope.senderClientId)

                // Should get a corresponding contact for the envelope entry
                // For each recipient
                TEST_RECIPIENTS.forEach { recipient ->
                    // Should get a matching recipient entry in the created envelope
                    val matchingRecipientEntry = envelope.recipients.first { recipientEntry ->
                        recipient.id == recipientEntry.userId
                    }

                    // All clients of this contact should have a matching payload in the entry
                    recipient.clients.forEach { client ->
                        val hasFoundClientEntry = matchingRecipientEntry.clientPayloads.any { clientPayload ->
                            clientPayload.clientId == client
                        }
                        assertTrue(hasFoundClientEntry)
                    }
                }
            }
    }

    @Test
    fun givenProteusThrowsDuringEncryption_whenCreatingBroadcastEnvelope_thenTheFailureShouldBePropagated() = runTest {
        val exception = ProteusException("OOPS", ProteusException.Code.PANIC, 15)
        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedThrowing(exception)
            .withEncodeReadableReturning(PlainMessageBlob(byteArrayOf()))
            .arrange()

        creator.createOutgoingBroadcastEnvelope(arrangement.proteusContext, TEST_RECIPIENTS, TestMessage.BROADCAST_MESSAGE)
            .shouldFail {
                assertIs<ProteusFailure>(it)
                assertEquals(exception, it.proteusException)
            }
    }

    @Test
    fun givenProteusThrowsDuringEncryption_whenCreatingBroadcastEnvelope_thenNoMoreEncryptionsShouldBeAttempted() = runTest {
        val (arrangement, creator) = Arrangement()
            .withEncryptBatchedThrowing(ProteusException("OOPS", ProteusException.Code.PANIC, 15))
            .withEncodeReadableReturning(PlainMessageBlob(byteArrayOf()))
            .arrange()

        creator.createOutgoingBroadcastEnvelope(arrangement.proteusContext, TEST_RECIPIENTS, TestMessage.BROADCAST_MESSAGE)

        coVerify {
            arrangement.proteusContext.encryptBatched(any(), any())
        }.wasInvoked(exactly = once)
    }

    private class Arrangement : ProteusCoreCryptoContextArrangement by ProteusCoreCryptoContextArrangementImpl() {
        private val selfUserId: UserId = UserId("user-id", "domain")

        val proteusClient: ProteusClient = mock(ProteusClient::class)
        private val protoContentMapper: ProtoContentMapper = mock(ProtoContentMapper::class)
        val conversationRepository: ConversationRepository = mock(ConversationRepository::class)
        val legalHoldStatusMapper: LegalHoldStatusMapper = mock(LegalHoldStatusMapper::class)

        val messageEnvelopeCreator: MessageEnvelopeCreator = MessageEnvelopeCreatorImpl(
            conversationRepository = conversationRepository,
            legalHoldStatusMapper = legalHoldStatusMapper,
            selfUserId = selfUserId,
            protoContentMapper = protoContentMapper
        )

        suspend fun withEncryptBatchedReturning(expected: Map<CryptoSessionId, ByteArray>,
                                                message: Matcher<ByteArray> = AnyMatcher(valueOf())
                                                ) = apply {
            coEvery { proteusContext.encryptBatched(matches { message.matches(it) }, any()) } returns expected
        }

        suspend fun withEncryptBatchedThrowing(exception: Throwable) = apply {
            coEvery { proteusContext.encryptBatched(any(), any()) } throws exception
        }

        fun withEncodeReadableReturning(blob: PlainMessageBlob) = apply {
            every { protoContentMapper.encodeToProtobuf(matches { it is ProtoContent.Readable }) } returns blob
        }

        fun withEncodeExternalReturning(blob: PlainMessageBlob) = apply {
            every { protoContentMapper.encodeToProtobuf(matches { it is ProtoContent.ExternalMessageInstructions })} returns blob
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit = {}) = let {
            runBlocking { block() }
            coEvery { conversationRepository.observeLegalHoldStatus(any()) } returns flowOf(Either.Right(Conversation.LegalHoldStatus.DISABLED))
            every { legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any()) } returns Conversation.LegalHoldStatus.DISABLED

            this to messageEnvelopeCreator
        }
    }

    private companion object {
        /**
         * Maximum envelope size before a message becomes so big that it would be sent as an external message
         */
        const val NON_EXTERNAL_CONTENT_SIZE_LIMIT = 200 * 1024
        val TEST_CONTACT_CLIENT_1 = ClientId("clientId1")
        val TEST_CONTACT_CLIENT_2 = ClientId("clientId2")
        val TEST_MEMBER_1 = UserId("value1", "domain1")
        val TEST_RECIPIENT_1 = Recipient(TEST_MEMBER_1, listOf(TEST_CONTACT_CLIENT_1, TEST_CONTACT_CLIENT_2))
        val TEST_CONTACT_CLIENT_3 = ClientId("clientId3")
        val TEST_MEMBER_2 = UserId("value2", "domain2")
        val TEST_RECIPIENT_2 = Recipient(TEST_MEMBER_2, listOf(TEST_CONTACT_CLIENT_3))
        val TEST_RECIPIENTS = listOf(TEST_RECIPIENT_1, TEST_RECIPIENT_2)
        val SELF_USER_ID = UserId("user-id", "domain")
    }
}
