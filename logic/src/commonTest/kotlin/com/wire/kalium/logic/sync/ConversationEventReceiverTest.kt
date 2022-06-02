package com.wire.kalium.logic.sync

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.handler.MessageTextEditHandler
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Text
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ConversationEventReceiverTest {

    @Test
    fun givenNewMessageEvent_whenHandling_shouldAskProteusClientForDecryption() = runTest {
        val (arrangement, eventReceiver) = Arrangement()
            .withSelfUserIdReturning(TestUser.USER_ID)
            .withUpdateConversationNotificationDateReturning(Either.Right(Unit))
            .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
            .withProteusClientDecryptingByteArray(decryptedData = byteArrayOf())
            .withProtoContentMapperReturning(any(), ProtoContent.Readable("uuid", MessageContent.Unknown))
            .arrange()

        val encodedEncryptedContent = Base64.encodeToBase64("Hello".encodeToByteArray())
        val messageEvent = arrangement.newMessageEvent(encodedEncryptedContent.decodeToString())
        eventReceiver.onEvent(messageEvent)

        val cryptoSessionId = CryptoSessionId(
            CryptoUserID(messageEvent.senderUserId.value, messageEvent.senderUserId.domain),
            CryptoClientId(messageEvent.senderClientId.value)
        )

        val decodedByteArray = Base64.decodeFromBase64(messageEvent.content.toByteArray())
        verify(arrangement.proteusClient)
            .suspendFunction(arrangement.proteusClient::decrypt)
            .with(matching { it.contentEquals(decodedByteArray) }, eq(cryptoSessionId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewMessageEventWithExternalContent_whenHandling_shouldPersistMessageWithDecryptedExternalMessage() = runTest {
        val aesKey = generateRandomAES256Key()
        val externalInstructions = ProtoContent.ExternalMessageInstructions(
            "uuid",
            aesKey.data,
            sha256 = null,
            encryptionAlgorithm = null
        )
        val plainTextContent = "Hello!"
        val protobufExternalContent = GenericMessage(content = GenericMessage.Content.Text(Text(plainTextContent)))
        val encryptedProtobufExternalContent = encryptDataWithAES256(PlainData(protobufExternalContent.encodeToByteArray()), aesKey)
        val decryptedExternalContent = MessageContent.Text(plainTextContent)
        val emptyArray = byteArrayOf()

        val (arrangement, eventReceiver) = Arrangement()
            .withSelfUserIdReturning(TestUser.USER_ID)
            .withUpdateConversationNotificationDateReturning(Either.Right(Unit))
            .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
            .withProteusClientDecryptingByteArray(decryptedData = emptyArray)
            .withRepositoryPersistingMessageDateReturning(Either.Right(Unit))
            .withProtoContentMapperReturning(matching { it.data.contentEquals(emptyArray) }, externalInstructions)
            .withProtoContentMapperReturning(
                matching { it.data.contentEquals(protobufExternalContent.encodeToByteArray()) },
                ProtoContent.Readable("uuid", decryptedExternalContent)
            ).arrange()

        val messageEvent = arrangement.newMessageEvent(
            Base64.encodeToBase64("anything".encodeToByteArray()).decodeToString(),
            encryptedExternalContent = encryptedProtobufExternalContent
        )

        eventReceiver.onEvent(messageEvent)

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::persistMessage)
            .with(matching { it.content == decryptedExternalContent })
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val proteusClient = mock(classOf<ProteusClient>())

        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        private val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        private val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        @Mock
        val protoContentMapper = mock(classOf<ProtoContentMapper>())

        @Mock
        private val callManager = mock(classOf<CallManager>())

        private val conversationEventReceiver: ConversationEventReceiver = ConversationEventReceiverImpl(
            proteusClient,
            messageRepository,
            conversationRepository,
            mlsConversationRepository,
            userRepository,
            protoContentMapper,
            lazyOf(callManager),
            MessageTextEditHandler(messageRepository)
        )

        fun withProteusClientDecryptingByteArray(decryptedData: ByteArray) = apply {
            given(proteusClient)
                .suspendFunction(proteusClient::decrypt)
                .whenInvokedWith(any(), any())
                .thenReturn(decryptedData)
        }

        fun withProtoContentMapperReturning(plainBlobMatcher: Matcher<PlainMessageBlob>, protoContent: ProtoContent) = apply {
            given(protoContentMapper)
                .function(protoContentMapper::decodeFromProtobuf)
                .whenInvokedWith(plainBlobMatcher)
                .thenReturn(protoContent)
        }

        fun withSelfUserIdReturning(selfUserId: UserId) = apply {
            given(userRepository)
                .suspendFunction(userRepository::getSelfUserId)
                .whenInvoked()
                .thenReturn(selfUserId)
        }

        fun withUpdateConversationNotificationDateReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationNotificationDate)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withRepositoryPersistingMessageDateReturning(result: Either<CoreFailure, Unit>) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::persistMessage)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withUpdateConversationModifiedDateReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationModifiedDate)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun newMessageEvent(
            encryptedContent: String,
            senderUserId: UserId = TestUser.USER_ID,
            encryptedExternalContent: EncryptedData? = null
        ) = Event.Conversation.NewMessage(
            "eventId",
            TestConversation.ID,
            senderUserId,
            TestClient.CLIENT_ID,
            "time",
            encryptedContent,
            encryptedExternalContent
        )

        fun arrange() = this to conversationEventReceiver
    }
}
