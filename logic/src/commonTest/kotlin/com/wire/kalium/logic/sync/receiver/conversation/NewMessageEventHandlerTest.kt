package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.DecryptedMessageBundle
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.conversation.ClearConversationContentImpl
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.message.ClearConversationContentHandler
import com.wire.kalium.logic.sync.receiver.message.DeleteForMeHandler
import com.wire.kalium.logic.sync.receiver.message.LastReadContentHandler
import com.wire.kalium.logic.sync.receiver.message.MessageTextEditHandler
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Text
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class NewMessageEventHandlerTest {

    @Test
    fun givenNewMessageEvent_whenHandling_shouldAskProteusClientForDecryption() = runTest {
        val (arrangement, eventReceiver) = Arrangement()
            .withProteusClientDecryptingByteArray(decryptedData = byteArrayOf())
            .withProtoContentMapperReturning(any(), ProtoContent.Readable("uuid", MessageContent.Unknown()))
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        val encodedEncryptedContent = Base64.encodeToBase64("Hello".encodeToByteArray())
        val messageEvent = arrangement.newMessageEvent(encodedEncryptedContent.decodeToString())
        eventReceiver.handleNewProteusMessage(messageEvent)

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
    fun givenValidNewImageMessageEvent_whenHandling_shouldSetDownloadStatusAsInProgress() = runTest {
        val validImageContent = MessageContent.Asset(
            AssetContent(
                1000, "some-image.jpg", "image/jpg", AssetContent.AssetMetadata.Image(200, 200),
                AssetContent.RemoteData(
                    ByteArray(16), ByteArray(16), "assetid", null, null, null
                ),
                Message.UploadStatus.NOT_UPLOADED, Message.DownloadStatus.NOT_DOWNLOADED
            )
        )
        val coreFailure = StorageFailure.DataNotFound
        val (arrangement, eventReceiver) = Arrangement()
            .withProteusClientDecryptingByteArray(decryptedData = byteArrayOf())
            .withProtoContentMapperReturning(any(), ProtoContent.Readable("uuid", validImageContent))
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFileSharingEnabled()
            .withErrorGetMessageById(coreFailure)
            .arrange()

        val encodedEncryptedContent = Base64.encodeToBase64("Hello".encodeToByteArray())
        val messageEvent = arrangement.newMessageEvent(encodedEncryptedContent.decodeToString())
        eventReceiver.handleNewProteusMessage(messageEvent)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(
                matching {
                    it.content is MessageContent.Asset &&
                            (it.content as MessageContent.Asset).value.downloadStatus == Message.DownloadStatus.DOWNLOAD_IN_PROGRESS
                }
            )
            .wasInvoked()
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
            .withProteusClientDecryptingByteArray(decryptedData = emptyArray)
            .withPersistingMessageReturning(Either.Right(Unit))
            .withConversationUpdateConversationReadDate(Either.Right(Unit))
            .withProtoContentMapperReturning(matching { it.data.contentEquals(emptyArray) }, externalInstructions)
            .withProtoContentMapperReturning(
                matching { it.data.contentEquals(protobufExternalContent.encodeToByteArray()) },
                ProtoContent.Readable(messageUid, decryptedExternalContent)
            ).arrange()

        val messageEvent = arrangement.newMessageEvent(
            Base64.encodeToBase64("anything".encodeToByteArray()).decodeToString(),
            encryptedExternalContent = encryptedProtobufExternalContent
        )

        eventReceiver.handleNewProteusMessage(messageEvent)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching { it.content == decryptedExternalContent })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewMLSMessageEventWithProposal_whenHandling_thenScheduleProposalTimer() = runTest {
        val eventTimestamp = Clock.System.now()
        val commitDelay: Long = 10

        val (arrangement, eventReceiver) = Arrangement()
            .withMLSClientProviderReturningClient()
            .withGetConversation(TestConversation.MLS_CONVERSATION)
            .withDecryptMessageReturningProposal(commitDelay)
            .withScheduleCommitSucceeding()
            .arrange()

        val messageEvent = arrangement.newMLSMessageEvent(eventTimestamp)
        eventReceiver.handleNewMLSMessage(messageEvent)

        verify(arrangement.pendingProposalScheduler)
            .suspendFunction(arrangement.pendingProposalScheduler::scheduleCommit)
            .with(eq(TestConversation.GROUP_ID), eq(eventTimestamp.plus(commitDelay.seconds)))
            .wasInvoked(once)
    }

    private class Arrangement {
        @Mock
        val proteusClient = mock(classOf<ProteusClient>())

        @Mock
        val proteusClientProvider = mock(classOf<ProteusClientProvider>())

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        val mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        val assetRepository = mock(classOf<AssetRepository>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val selfConversationIdProvider = mock(SelfConversationIdProvider::class)

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        @Mock
        val protoContentMapper = mock(classOf<ProtoContentMapper>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        @Mock
        private val callManager = mock(classOf<CallManager>())

        @Mock
        val pendingProposalScheduler = mock(classOf<PendingProposalScheduler>())

        @Mock
        val persistReactionsUseCase = mock(classOf<PersistReactionUseCase>())

        private val newMessageEventHandler: NewMessageEventHandler = NewMessageEventHandlerImpl(
            proteusClientProvider,
            mlsClientProvider,
            userRepository,
            assetRepository,
            messageRepository,
            userConfigRepository,
            conversationRepository,
            lazyOf(callManager),
            persistMessage,
            persistReactionsUseCase,
            MessageTextEditHandler(messageRepository),
            LastReadContentHandler(
                conversationRepository = conversationRepository,
                selfUserId = TestUser.USER_ID,
                selfConversationIdProvider = selfConversationIdProvider
            ),
            ClearConversationContentHandler(
                clearConversationContent = ClearConversationContentImpl(conversationRepository, assetRepository),
                selfUserId = TestUser.USER_ID,
                selfConversationIdProvider = selfConversationIdProvider
            ),
            DeleteForMeHandler(
                conversationRepository = conversationRepository,
                messageRepository = messageRepository,
                selfUserId = TestUser.USER_ID,
                selfConversationIdProvider = selfConversationIdProvider
            ),
            pendingProposalScheduler,
            protoContentMapper = protoContentMapper
        )

        init {
            given(proteusClientProvider)
                .suspendFunction(proteusClientProvider::getOrError)
                .whenInvoked()
                .thenReturn(Either.Right(proteusClient))
        }

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

        fun withPersistingMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withFileSharingEnabled() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::isFileSharingEnabled)
                .whenInvoked()
                .thenReturn(Either.Right(FileSharingStatus(true, false)))
        }

        fun withErrorGetMessageById(coreFailure: CoreFailure) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(coreFailure))
        }

        fun withConversationUpdateConversationReadDate(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationReadDate)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withMLSClientProviderReturningClient() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .then { Either.Right(mlsClient) }
        }

        fun withDecryptMessageReturningProposal(commitDelay: Long = 15) = apply {
            given(mlsClient)
                .function(mlsClient::decryptMessage)
                .whenInvokedWith(anything<String>(), anything<ByteArray>())
                .thenReturn(DecryptedMessageBundle(null, commitDelay, null))
        }

        fun withScheduleCommitSucceeding() = apply {
            given(pendingProposalScheduler)
                .suspendFunction(pendingProposalScheduler::scheduleCommit)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
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

        fun newMLSMessageEvent(
            timestamp: Instant
        ) = Event.Conversation.NewMLSMessage(
            "eventId",
            TestConversation.ID,
            TestUser.USER_ID,
            timestamp.toString(),
            "content"
        )

        fun withGetConversation(conversation: Conversation? = TestConversation.CONVERSATION) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationById)
                .whenInvokedWith(any())
                .thenReturn(conversation)
        }

        fun arrange() = this to newMessageEventHandler
    }

}
