package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.anything
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.wire.kalium.cryptography.UserId as CryptoUserId

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

        messageEnvelopeCreator.createOutgoingEnvelope(recipients, TEST_MESSAGE)

        recipients.forEach { recipient ->
            recipient.clients.forEach { client ->
                verify(proteusClient)
                    .suspendFunction(proteusClient::encrypt)
                    .with(eq(plainData), eq(CryptoSessionId(CryptoUserId(recipient.member.id.value), CryptoClientId(client.value))))
                    .wasInvoked(exactly = once)
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

        messageEnvelopeCreator.createOutgoingEnvelope(recipients, TEST_MESSAGE)
            .shouldSucceed { envelope ->
                assertEquals(TEST_SENDER_CLIENT_ID, envelope.senderClientId)

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

        messageEnvelopeCreator.createOutgoingEnvelope(TEST_RECIPIENTS, TEST_MESSAGE)
            .shouldFail {
                assertEquals(CoreFailure.Unknown(exception), it)
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

        messageEnvelopeCreator.createOutgoingEnvelope(TEST_RECIPIENTS, TEST_MESSAGE)

        verify(proteusClient)
            .suspendFunction(proteusClient::encrypt)
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    private companion object {
        val TEST_SENDER_USER_ID = TestUser.USER_ID
        val TEST_SENDER_CLIENT_ID = TestClient.CLIENT_ID
        const val TEST_MESSAGE_ID = "messageId"
        val TEST_CONTENT = MessageContent.Text("Ciao!")
        val TEST_MESSAGE = Message(
            TEST_MESSAGE_ID,
            TEST_CONTENT,
            ConversationId("conv", "id"),
            "date",
            TEST_SENDER_USER_ID,
            TEST_SENDER_CLIENT_ID,
            Message.Status.PENDING
        )
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
