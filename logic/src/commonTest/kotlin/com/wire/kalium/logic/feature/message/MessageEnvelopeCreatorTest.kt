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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.client.ProteusClientProvider
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
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.anything
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageEnvelopeCreatorTest {

    @Mock
    private val proteusClient = mock(ProteusClient::class)

    @Mock
    private val proteusClientProvider = mock(ProteusClientProvider::class)

    @Mock
    private val protoContentMapper = mock(ProtoContentMapper::class)

    @Mock
    private val conversationRepository = mock(ConversationRepository::class)

    @Mock
    private val legalHoldStatusMapper = mock(LegalHoldStatusMapper::class)

    private lateinit var messageEnvelopeCreator: MessageEnvelopeCreator

    init {
        given(proteusClientProvider)
            .suspendFunction(proteusClientProvider::getOrError)
            .whenInvoked()
            .thenReturn(Either.Right(proteusClient))
    }

    @BeforeTest
    fun setup() {
        messageEnvelopeCreator = MessageEnvelopeCreatorImpl(
            conversationRepository,
            legalHoldStatusMapper,
            proteusClientProvider,
            SELF_USER_ID,
            protoContentMapper
        )

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeLegalHoldStatus)
            .whenInvokedWith(anything())
            .then { flowOf(Either.Right(Conversation.LegalHoldStatus.DISABLED)) }

        given(legalHoldStatusMapper)
            .function(legalHoldStatusMapper::mapLegalHoldConversationStatus)
            .whenInvokedWith(anything(), anything())
            .thenReturn(Conversation.LegalHoldStatus.DISABLED)
    }

    @Test
    fun givenRecipients_whenCreatingAnEnvelope_thenProteusClientShouldBeUsedToEncryptForEachClient() = runTest {
        val recipients = TEST_RECIPIENTS
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map { CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        val encryptedData = byteArrayOf()
        given(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .whenInvokedWith(anything(), anything())
            .thenReturn(sessionIds.associateWith { encryptedData })

        val plainData = byteArrayOf(0x42, 0x73)
        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(anything())
            .thenReturn(PlainMessageBlob(plainData))

        messageEnvelopeCreator.createOutgoingEnvelope(recipients, TestMessage.TEXT_MESSAGE)

        verify(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .with(
                eq(plainData),
                eq(sessionIds)
            )

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeLegalHoldStatus)
            .with(anything())
            .wasInvoked(once)

        verify(legalHoldStatusMapper)
            .function(legalHoldStatusMapper::mapLegalHoldConversationStatus)
            .with(anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenMessageContentIsTooBig_whenCreatingAnEnvelope_thenShouldCreateExternalMessageInstructions() = runTest {
        // Given
        // A big byte array as the readable content
        val plainData = ByteArray(SUPER_BIG_CONTENT_SIZE) { it.toByte() }

        val recipients = TEST_RECIPIENTS
        val externalInstructionsArray = byteArrayOf(0x42, 0x13)
        val encryptedData = byteArrayOf(0x66)
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map { CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        // Should only attempt to E2EE the external instructions, not the content itself
        given(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .whenInvokedWith(matching { it.contentEquals(externalInstructionsArray) }, anything())
            .thenReturn(sessionIds.associateWith { encryptedData })

        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(matching { it is ProtoContent.Readable })
            .thenReturn(PlainMessageBlob(plainData))

        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(matching { it is ProtoContent.ExternalMessageInstructions })
            .thenReturn(PlainMessageBlob(externalInstructionsArray))

        // When
        val envelope = messageEnvelopeCreator.createOutgoingEnvelope(recipients, TestMessage.TEXT_MESSAGE)

        // Then
        envelope.shouldSucceed {
            assertTrue { it.dataBlob!!.data.size >= SUPER_BIG_CONTENT_SIZE }

            it.recipients.forEach { recipientEntry ->
                recipientEntry.clientPayloads.forEach { clientPayload ->
                    assertEquals(encryptedData, clientPayload.payload.data)
                }
            }
        }

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeLegalHoldStatus)
            .with(anything())
            .wasInvoked(once)

        verify(legalHoldStatusMapper)
            .function(legalHoldStatusMapper::mapLegalHoldConversationStatus)
            .with(anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenMessageContentIsSmall_whenCreatingAnEnvelope_thenShouldNotCreateExternalMessageInstructions() = runTest {
        // Given
        val plainData = ByteArray(1) { it.toByte() }

        val recipients = TEST_RECIPIENTS
        val encryptedData = byteArrayOf(0x66)
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map { CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        // Should only attempt to E2EE the content itself
        given(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .whenInvokedWith(matching { it.contentEquals(plainData) }, anything())
            .thenReturn(sessionIds.associateWith { encryptedData })

        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(matching { it is ProtoContent.Readable })
            .thenReturn(PlainMessageBlob(plainData))

        // When
        val envelope = messageEnvelopeCreator.createOutgoingEnvelope(recipients, TestMessage.TEXT_MESSAGE)

        // Then
        envelope.shouldSucceed {
            assertNull(it.dataBlob)

            it.recipients.forEach { recipientEntry ->
                recipientEntry.clientPayloads.forEach { clientPayload ->
                    assertEquals(encryptedData, clientPayload.payload.data)
                }
            }
        }

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeLegalHoldStatus)
            .with(anything())
            .wasInvoked(once)

        verify(legalHoldStatusMapper)
            .function(legalHoldStatusMapper::mapLegalHoldConversationStatus)
            .with(anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenEncryptionSucceeds_whenCreatingAnEnvelope_thenTheResultShouldContainAllEntries() = runTest {
        val recipients = TEST_RECIPIENTS
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map { CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        val encryptedData = byteArrayOf()
        given(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .whenInvokedWith(anything(), anything())
            .thenReturn(sessionIds.associateWith { encryptedData })

        val plainData = byteArrayOf(0x42, 0x73)
        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(anything())
            .thenReturn(PlainMessageBlob(plainData))

        messageEnvelopeCreator.createOutgoingEnvelope(recipients, TestMessage.TEXT_MESSAGE)
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

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeLegalHoldStatus)
            .with(anything())
            .wasInvoked(once)

        verify(legalHoldStatusMapper)
            .function(legalHoldStatusMapper::mapLegalHoldConversationStatus)
            .with(anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenProteusThrowsDuringEncryption_whenCreatingEnvelope_thenTheFailureShouldBePropagated() = runTest {
        val exception = ProteusException("OOPS", ProteusException.Code.PANIC)
        given(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .whenInvokedWith(anything(), anything())
            .thenThrow(exception)

        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(anything())
            .thenReturn(PlainMessageBlob(byteArrayOf()))

        messageEnvelopeCreator.createOutgoingEnvelope(TEST_RECIPIENTS, TestMessage.TEXT_MESSAGE)
            .shouldFail {
                assertIs<ProteusFailure>(it)
                assertEquals(exception, it.proteusException)
            }

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeLegalHoldStatus)
            .with(anything())
            .wasInvoked(once)

        verify(legalHoldStatusMapper)
            .function(legalHoldStatusMapper::mapLegalHoldConversationStatus)
            .with(anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenProteusThrowsDuringEncryption_whenCreatingEnvelope_thenNoMoreEncryptionsShouldBeAttempted() = runTest {
        given(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .whenInvokedWith(anything(), anything())
            .thenThrow(ProteusException("OOPS", ProteusException.Code.PANIC))

        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(anything())
            .thenReturn(PlainMessageBlob(byteArrayOf()))

        messageEnvelopeCreator.createOutgoingEnvelope(TEST_RECIPIENTS, TestMessage.TEXT_MESSAGE)

        verify(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .with(anything(), anything())
            .wasInvoked(exactly = once)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeLegalHoldStatus)
            .with(anything())
            .wasInvoked(once)

        verify(legalHoldStatusMapper)
            .function(legalHoldStatusMapper::mapLegalHoldConversationStatus)
            .with(anything(), anything())
            .wasInvoked(once)
    }
    @Test
    fun givenRecipients_whenCreatingBroadcastEnvelope_thenProteusClientShouldBeUsedToEncryptForEachClient() = runTest {
        val recipients = TEST_RECIPIENTS
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map { CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        val encryptedData = byteArrayOf()
        given(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .whenInvokedWith(anything(), anything())
            .thenReturn(sessionIds.associateWith { encryptedData })

        val plainData = byteArrayOf(0x42, 0x73)
        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(anything())
            .thenReturn(PlainMessageBlob(plainData))

        messageEnvelopeCreator.createOutgoingBroadcastEnvelope(recipients, TestMessage.BROADCAST_MESSAGE)

        verify(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .with(
                eq(plainData),
                eq(sessionIds)
            )
    }

    @Test
    fun givenMessageContentIsTooBig_whenCreatingBroadcastEnvelope_thenShouldCreateExternalMessageInstructions() = runTest {
        // Given
        // A big byte array as the readable content
        val plainData = ByteArray(SUPER_BIG_CONTENT_SIZE) { it.toByte() }

        val recipients = TEST_RECIPIENTS
        val externalInstructionsArray = byteArrayOf(0x42, 0x13)
        val encryptedData = byteArrayOf(0x66)
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map { CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        // Should only attempt to E2EE the external instructions, not the content itself
        given(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .whenInvokedWith(matching { it.contentEquals(externalInstructionsArray) }, anything())
            .thenReturn(sessionIds.associateWith { encryptedData })

        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(matching { it is ProtoContent.Readable })
            .thenReturn(PlainMessageBlob(plainData))

        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(matching { it is ProtoContent.ExternalMessageInstructions })
            .thenReturn(PlainMessageBlob(externalInstructionsArray))

        // When
        val envelope = messageEnvelopeCreator.createOutgoingBroadcastEnvelope(recipients, TestMessage.BROADCAST_MESSAGE)

        // Then
        envelope.shouldSucceed {
            assertTrue { it.dataBlob!!.data.size >= SUPER_BIG_CONTENT_SIZE }

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
            recipient.clients.map { CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        // Should only attempt to E2EE the content itself
        given(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .whenInvokedWith(matching { it.contentEquals(plainData) }, anything())
            .thenReturn(sessionIds.associateWith { encryptedData })

        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(matching { it is ProtoContent.Readable })
            .thenReturn(PlainMessageBlob(plainData))

        // When
        val envelope = messageEnvelopeCreator.createOutgoingBroadcastEnvelope(recipients, TestMessage.BROADCAST_MESSAGE)

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
        val recipients = TEST_RECIPIENTS
        val sessionIds = recipients.flatMap { recipient ->
            recipient.clients.map { CryptoSessionId(recipient.id.toCrypto(), CryptoClientId((it.value)))
            }
        }

        val encryptedData = byteArrayOf()
        given(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .whenInvokedWith(anything(), anything())
            .thenReturn(sessionIds.associateWith { encryptedData })

        val plainData = byteArrayOf(0x42, 0x73)
        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(anything())
            .thenReturn(PlainMessageBlob(plainData))

        messageEnvelopeCreator.createOutgoingBroadcastEnvelope(recipients, TestMessage.BROADCAST_MESSAGE)
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
        val exception = ProteusException("OOPS", ProteusException.Code.PANIC)
        given(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .whenInvokedWith(anything(), anything())
            .thenThrow(exception)

        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(anything())
            .thenReturn(PlainMessageBlob(byteArrayOf()))

        messageEnvelopeCreator.createOutgoingBroadcastEnvelope(TEST_RECIPIENTS, TestMessage.BROADCAST_MESSAGE)
            .shouldFail {
                assertIs<ProteusFailure>(it)
                assertEquals(exception, it.proteusException)
            }
    }

    @Test
    fun givenProteusThrowsDuringEncryption_whenCreatingBroadcastEnvelope_thenNoMoreEncryptionsShouldBeAttempted() = runTest {
        given(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .whenInvokedWith(anything(), anything())
            .thenThrow(ProteusException("OOPS", ProteusException.Code.PANIC))

        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(anything())
            .thenReturn(PlainMessageBlob(byteArrayOf()))

        messageEnvelopeCreator.createOutgoingBroadcastEnvelope(TEST_RECIPIENTS, TestMessage.BROADCAST_MESSAGE)

        verify(proteusClient)
            .suspendFunction(proteusClient::encryptBatched)
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    private companion object {
        /**
         * A content size so big it would alone go over the 256KB limit in the backend
         */
        const val SUPER_BIG_CONTENT_SIZE = 260 * 1024
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
