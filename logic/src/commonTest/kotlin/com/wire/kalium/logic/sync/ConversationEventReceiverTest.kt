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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.handler.MessageTextEditHandler
import com.wire.kalium.logic.test_util.wasInTheLastSecond
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toInstant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationEventReceiverTest {

    @Test
    fun givenNewMessageEvent_whenHandling_shouldAskProteusClientForDecryption() = runTest {
        val (arrangement, eventReceiver) = Arrangement()
            .withSelfUserIdReturning(TestUser.USER_ID)
            .withProteusClientDecryptingByteArray(decryptedData = byteArrayOf())
            .withProtoContentMapperReturning(any(), ProtoContent.Readable("uuid", MessageContent.Unknown()))
            .withPersistingMessageReturning(Either.Right(Unit))
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
        val messageUid = "uuid"
        val externalInstructions = ProtoContent.ExternalMessageInstructions(
            messageUid,
            aesKey.data,
            sha256 = null,
            encryptionAlgorithm = null
        )
        val plainTextContent = "Hello!"

        val protobufExternalContent = GenericMessage(
            content = GenericMessage.Content.Text(Text(plainTextContent)),
            messageId = messageUid
        )
        val encryptedProtobufExternalContent = encryptDataWithAES256(PlainData(protobufExternalContent.encodeToByteArray()), aesKey)
        val decryptedExternalContent = MessageContent.Text(plainTextContent)
        val emptyArray = byteArrayOf()

        val (arrangement, eventReceiver) = Arrangement()
            .withSelfUserIdReturning(TestUser.USER_ID)
            .withProteusClientDecryptingByteArray(decryptedData = emptyArray)
            .withPersistingMessageReturning(Either.Right(Unit))
            .withProtoContentMapperReturning(matching { it.data.contentEquals(emptyArray) }, externalInstructions)
            .withProtoContentMapperReturning(
                matching { it.data.contentEquals(protobufExternalContent.encodeToByteArray()) },
                ProtoContent.Readable(messageUid, decryptedExternalContent)
            ).arrange()

        val messageEvent = arrangement.newMessageEvent(
            Base64.encodeToBase64("anything".encodeToByteArray()).decodeToString(),
            encryptedExternalContent = encryptedProtobufExternalContent
        )

        eventReceiver.onEvent(messageEvent)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching { it.content == decryptedExternalContent })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewConversationEvent_whenHandlingIt_thenInsertConversationFromEventShouldBeCalled() = runTest {
        val event = Event.Conversation.NewConversation(
            id = "eventId",
            conversationId = TestConversation.ID,
            timestampIso = "timestamp",
            conversation = TestConversation.CONVERSATION_RESPONSE
        )

        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
            .withInsertConversationFromEventReturning(Either.Right(Unit))
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::insertConversationFromEvent)
            .with(eq(event))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewConversationEvent_whenHandlingIt_thenConversationLastModifiedShouldBeUpdated() = runTest {
        val event = Event.Conversation.NewConversation(
            id = "eventId",
            conversationId = TestConversation.ID,
            timestampIso = "timestamp",
            conversation = TestConversation.CONVERSATION_RESPONSE
        )

        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
            .withInsertConversationFromEventReturning(Either.Right(Unit))
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationModifiedDate)
            .with(eq(event.conversationId), matching { it.toInstant().wasInTheLastSecond })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEvent_whenHandlingIt_thenShouldFetchConversationIfUnknown() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventReceiver) = Arrangement()
            .withSelfUserIdReturning(TestUser.USER_ID)
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownSucceeding()
            .withPersistMembersSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversationIfUnknown)
            .with(eq(event.conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEvent_whenHandlingIt_thenShouldPersistMembers() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventReceiver) = Arrangement()
            .withSelfUserIdReturning(TestUser.USER_ID)
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownSucceeding()
            .withPersistMembersSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::persistMembers)
            .with(eq(newMembers), eq(event.conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEventAndFetchConversationFails_whenHandlingIt_thenShouldAttemptPersistingMembersAnyway() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventReceiver) = Arrangement()
            .withSelfUserIdReturning(TestUser.USER_ID)
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withPersistMembersSucceeding()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::persistMembers)
            .with(eq(newMembers), eq(event.conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEvent_whenHandlingIt_thenShouldPersistSystemMessage() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Admin))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventReceiver) = Arrangement()
            .withSelfUserIdReturning(TestUser.USER_ID)
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withPersistMembersSucceeding()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                it is Message.System && it.content is MessageContent.MemberChange
            })
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val proteusClient = mock(classOf<ProteusClient>())

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        private val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        @Mock
        val protoContentMapper = mock(classOf<ProtoContentMapper>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        @Mock
        private val callManager = mock(classOf<CallManager>())

        private val conversationEventReceiver: ConversationEventReceiver = ConversationEventReceiverImpl(
            proteusClient,
            persistMessage,
            messageRepository,
            conversationRepository,
            mlsConversationRepository,
            userRepository,
            lazyOf(callManager),
            MessageTextEditHandler(messageRepository),
            protoContentMapper = protoContentMapper,
            userConfigRepository = userConfigRepository
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

        fun withPersistingMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withUpdateConversationModifiedDateReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationModifiedDate)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withInsertConversationFromEventReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::insertConversationFromEvent)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withFetchConversationIfUnknownSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversationIfUnknown)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withFetchConversationIfUnknownFailing(coreFailure: CoreFailure) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversationIfUnknown)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(coreFailure))
        }

        fun withPersistMembersSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::persistMembers)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withFetchUsersIfUnknownByIdsReturning(result: Either<StorageFailure, Unit>) = apply {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersIfUnknownByIds)
                .whenInvokedWith(any())
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
