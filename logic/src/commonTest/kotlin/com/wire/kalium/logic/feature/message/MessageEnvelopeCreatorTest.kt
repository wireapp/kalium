package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestMessage
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MessageEnvelopeCreatorTest {

    @Mock
    private val proteusClient = mock(ProteusClient::class)

    @Mock
    private val protoContentMapper = mock(ProtoContentMapper::class)

    private lateinit var messageEnvelopeCreator: MessageEnvelopeCreator

    @BeforeTest
    fun setup() {
        messageEnvelopeCreator = MessageEnvelopeCreatorImpl(proteusClient, protoContentMapper)
    }

    @Test
    fun givenRecipients_whenCreatingAnEnvelope_thenProteusClientShouldBeUsedToEncryptForEachClient() = runTest {
        val recipients = TEST_RECIPIENTS

        val encryptedData = byteArrayOf()
        given(proteusClient)
            .suspendFunction(proteusClient::encrypt)
            .whenInvokedWith(anything(), anything())
            .thenReturn(encryptedData)

        val plainData = byteArrayOf(0x42, 0x73)
        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(anything())
            .thenReturn(PlainMessageBlob(plainData))

        messageEnvelopeCreator.createOutgoingEnvelope(recipients, TestMessage.TEXT_MESSAGE)

        recipients.forEach { recipient ->
            recipient.clients.forEach { client ->
                verify(proteusClient)
                    .suspendFunction(proteusClient::encrypt)
                    .with(eq(plainData), eq(CryptoSessionId(CryptoUserID(recipient.member.id.value, recipient.member.id.domain), CryptoClientId(client.value))))
                    .wasInvoked(exactly = once)
            }
        }
    }

    @Test
    fun givenMessageContentIsTooBig_whenCreatingAnEnvelope_thenShouldCreateExternalMessageInstructions() = runTest {
        // Given
        // A big byte array as the readable content
        val plainData = ByteArray(SUPER_BIG_CONTENT_SIZE) { it.toByte() }

        val recipients = TEST_RECIPIENTS
        val externalInstructionsArray = byteArrayOf(0x42, 0x13)
        val encryptedData = byteArrayOf(0x66)
        // Should only attempt to E2EE the external instructions, not the content itself
        given(proteusClient)
            .suspendFunction(proteusClient::encrypt)
            .whenInvokedWith(matching { it.contentEquals(externalInstructionsArray) }, anything())
            .thenReturn(encryptedData)

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
    }
    @Test
    fun givenMessageContentIsSmall_whenCreatingAnEnvelope_thenShouldNotCreateExternalMessageInstructions() = runTest {
        // Given
        val plainData = ByteArray(1) { it.toByte() }

        val recipients = TEST_RECIPIENTS
        val encryptedData = byteArrayOf(0x66)
        // Should only attempt to E2EE the content itself
        given(proteusClient)
            .suspendFunction(proteusClient::encrypt)
            .whenInvokedWith(matching { it.contentEquals(plainData) }, anything())
            .thenReturn(encryptedData)

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
    }

    @Test
    fun givenEncryptionSucceeds_whenCreatingAnEnvelope_thenTheResultShouldContainAllEntries() = runTest {
        val recipients = TEST_RECIPIENTS

        val encryptedData = byteArrayOf()
        given(proteusClient)
            .suspendFunction(proteusClient::encrypt)
            .whenInvokedWith(anything(), anything())
            .thenReturn(encryptedData)

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
                        recipient.member.id == recipientEntry.userId
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
    fun givenProteusThrowsDuringEncryption_whenCreatingEnvelope_thenTheFailureShouldBePropagated() = runTest {
        val exception = ProteusException("OOPS", ProteusException.Code.PANIC)
        given(proteusClient)
            .suspendFunction(proteusClient::encrypt)
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
    }

    @Test
    fun givenProteusThrowsDuringEncryption_whenCreatingEnvelope_thenNoMoreEncryptionsShouldBeAttempted() = runTest {
        given(proteusClient)
            .suspendFunction(proteusClient::encrypt)
            .whenInvokedWith(anything(), anything())
            .thenThrow(ProteusException("OOPS", ProteusException.Code.PANIC))

        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(anything())
            .thenReturn(PlainMessageBlob(byteArrayOf()))

        messageEnvelopeCreator.createOutgoingEnvelope(TEST_RECIPIENTS, TestMessage.TEXT_MESSAGE)

        verify(proteusClient)
            .suspendFunction(proteusClient::encrypt)
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    private companion object {
        /**
         * A content size so big it would alone go over the 256KB limit in the backend
         */
        val SUPER_BIG_CONTENT_SIZE = 300 * 1024
        val TEST_CONTACT_CLIENT_1 = ClientId("clientId1")
        val TEST_CONTACT_CLIENT_2 = ClientId("clientId2")
        val TEST_MEMBER_1 = Member(UserId("value1", "domain1"))
        val TEST_RECIPIENT_1 = Recipient(TEST_MEMBER_1, listOf(TEST_CONTACT_CLIENT_1, TEST_CONTACT_CLIENT_2))
        val TEST_CONTACT_CLIENT_3 = ClientId("clientId3")
        val TEST_MEMBER_2 = Member(UserId("value2", "domain2"))
        val TEST_RECIPIENT_2 = Recipient(TEST_MEMBER_2, listOf(TEST_CONTACT_CLIENT_3))
        val TEST_RECIPIENTS = listOf(TEST_RECIPIENT_1, TEST_RECIPIENT_2)
    }
}
