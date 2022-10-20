package com.wire.kalium.logic.sync.receiver

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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
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
import com.wire.kalium.logic.feature.message.EphemeralNotificationsMgr
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MLSWelcomeEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MemberChangeEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.NewConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.NewMessageEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.message.ClearConversationContentHandler
import com.wire.kalium.logic.sync.receiver.message.DeleteForMeHandler
import com.wire.kalium.logic.sync.receiver.message.LastReadContentHandler
import com.wire.kalium.logic.sync.receiver.message.MessageTextEditHandler
import com.wire.kalium.logic.test_util.wasInTheLastSecond
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.persistence.dao.ConversationDAO
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
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

// TODO(refactor): Split into smaller specialised chunks and test specific receivers instead of the whole ConversationEventReceiver
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationEventReceiverTest {

    @Test
    fun givenNewMessageEvent_whenHandling_shouldAskProteusClientForDecryption() = runTest {
        val (arrangement, eventReceiver) = Arrangement()
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
        eventReceiver.onEvent(messageEvent)

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

        eventReceiver.onEvent(messageEvent)

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
        eventReceiver.onEvent(messageEvent)

        verify(arrangement.pendingProposalScheduler)
            .suspendFunction(arrangement.pendingProposalScheduler::scheduleCommit)
            .with(eq(TestConversation.GROUP_ID), eq(eventTimestamp.plus(commitDelay.seconds)))
            .wasInvoked(once)
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
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withPersistMembersSucceeding()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(
                matching {
                    it is Message.System && it.content is MessageContent.MemberChange
                }
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberChangeEvent_whenHandlingIt_thenShouldFetchConversationIfUnknown() = runTest {
        val updatedMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChange(member = updatedMember)

        val (arrangement, eventReceiver) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMemberSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversationIfUnknown)
            .with(eq(event.conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberChangeEventMutedStatus_whenHandlingIt_thenShouldUpdateConversation() = runTest {
        val event = TestEvent.memberChangeMutedStatus()

        val (arrangement, eventReceiver) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMutedStatusSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMutedStatus)
            .with(eq(event.conversationId), any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberChangeEvent_whenHandlingIt_thenShouldUpdateMembers() = runTest {
        val updatedMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChange(member = updatedMember)

        val (arrangement, eventReceiver) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMemberSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMemberFromEvent)
            .with(eq(updatedMember), eq(event.conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberChangeEventAndFetchConversationFails_whenHandlingIt_thenShouldAttemptUpdateMembersAnyway() = runTest {
        val updatedMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChange(member = updatedMember)

        val (arrangement, eventReceiver) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withUpdateMemberSucceeding()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMemberFromEvent)
            .with(eq(updatedMember), eq(event.conversationId))
    }

    @Test
    fun givenMemberChangeEventAndNotRolePresent_whenHandlingIt_thenShouldIgnoreTheEvent() = runTest {
        val updatedMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChangeIgnored()

        val (arrangement, eventReceiver) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withUpdateMemberSucceeding()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMemberFromEvent)
            .with(eq(updatedMember), eq(event.conversationId))
            .wasNotInvoked()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversationIfUnknown)
            .with(eq(event.conversationId))
            .wasNotInvoked()
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingItAndNotExists_thenShouldSkipTheDeletion() = runTest {
        val event = TestEvent.deletedConversation()
        val (arrangement, eventReceiver) = Arrangement()
            .withEphemeralNotificationEnqueue()
            .withGetConversation(null)
            .withGetUserAuthor(event.senderUserId)
            .withDeletingConversationSucceeding()
            .arrange()

        eventReceiver.onEvent(event)

        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::deleteConversation)
                .with(eq(TestConversation.ID))
                .wasNotInvoked()
        }
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingIt_thenShouldDeleteTheConversationAndItsContent() = runTest {
        val event = TestEvent.deletedConversation()
        val (arrangement, eventReceiver) = Arrangement()
            .withEphemeralNotificationEnqueue()
            .withGetConversation()
            .withGetUserAuthor(event.senderUserId)
            .withDeletingConversationSucceeding()
            .arrange()

        eventReceiver.onEvent(event)

        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::deleteConversation)
                .with(eq(TestConversation.ID))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAConversationEventRenamed_whenHandlingIt_thenShouldRenameTheConversation() = runTest {
        val event = TestEvent.renamedConversation()
        val (arrangement, eventReceiver) = Arrangement()
            .withGetConversation()
            .withGetUserAuthor(event.senderUserId)
            .withRenamingConversationReturning()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        eventReceiver.onEvent(event)

        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationName)
                .with(eq(TestConversation.ID), any(), any())
                .wasInvoked(exactly = once)

            verify(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAConversationEventRenamed_whenHandlingItFails_thenShouldNotUpdateTheConversation() = runTest {
        val event = TestEvent.renamedConversation()
        val (arrangement, eventReceiver) = Arrangement()
            .withGetConversation()
            .withGetUserAuthor(event.senderUserId)
            .withRenamingConversationReturning(Either.Left(StorageFailure.DataNotFound))
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        eventReceiver.onEvent(event)

        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationName)
                .with(eq(TestConversation.ID), any(), any())
                .wasInvoked(exactly = once)

            verify(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .with(any())
                .wasNotInvoked()
        }
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
        val conversationDAO = mock(classOf<ConversationDAO>())

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
        private val ephemeralNotifications = mock(classOf<EphemeralNotificationsMgr>())

        @Mock
        val pendingProposalScheduler = mock(classOf<PendingProposalScheduler>())

        @Mock
        val persistReactionsUseCase = mock(classOf<PersistReactionUseCase>())

        private val conversationEventReceiver: ConversationEventReceiver = ConversationEventReceiverImpl(
            NewMessageEventHandlerImpl(
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
            ),
            NewConversationEventHandlerImpl(conversationRepository),
            DeletedConversationEventHandlerImpl(
                userRepository,
                conversationRepository,
                ephemeralNotifications
            ),
            MemberJoinEventHandlerImpl(
                conversationRepository,
                persistMessage
            ),
            MemberLeaveEventHandlerImpl(
                conversationDAO,
                userRepository,
                persistMessage
            ),
            MemberChangeEventHandlerImpl(conversationRepository),
            MLSWelcomeEventHandlerImpl(mlsClientProvider, conversationDAO),
            RenamedConversationEventHandlerImpl(
                conversationRepository,
                persistMessage
            )
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

        fun withGetMessageById(expectedMessage: Message.Regular) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(expectedMessage))
        }

        fun withErrorGetMessageById(coreFailure: CoreFailure) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(coreFailure))
        }

        fun withUpdateConversationModifiedDateReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationModifiedDate)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withConversationUpdateConversationReadDate(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationReadDate)
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

        fun withUpdateMemberSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateMemberFromEvent)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withUpdateMutedStatusSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateMutedStatus)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withFetchUsersIfUnknownByIdsReturning(result: Either<StorageFailure, Unit>) = apply {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersIfUnknownByIds)
                .whenInvokedWith(any())
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

        fun withDeletingConversationSucceeding(conversationId: ConversationId = TestConversation.ID) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::deleteConversation)
                .whenInvokedWith((eq(conversationId)))
                .thenReturn(Either.Right(Unit))
        }

        fun withRenamingConversationReturning(result: Either<CoreFailure, Unit> = Either.Right(Unit)) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationName)
                .whenInvokedWith(eq(TestConversation.ID), eq("newName"), any())
                .thenReturn(result)
        }

        fun withGetConversation(conversation: Conversation? = TestConversation.CONVERSATION) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationById)
                .whenInvokedWith(any())
                .thenReturn(conversation)
        }

        fun withGetUserAuthor(userId: UserId = TestUser.USER_ID) = apply {
            given(userRepository)
                .suspendFunction(userRepository::observeUser)
                .whenInvokedWith(eq(userId))
                .thenReturn(flowOf(TestUser.OTHER))
        }

        fun withEphemeralNotificationEnqueue() = apply {
            given(ephemeralNotifications)
                .suspendFunction(ephemeralNotifications::scheduleNotification)
                .whenInvokedWith(any())
                .thenDoNothing()
        }

        suspend fun withSelfConversationId(conversationId: ConversationId) = apply {
            given(selfConversationIdProvider).coroutine { invoke() }.then { Either.Right(conversationId) }
        }

        fun arrange() = this to conversationEventReceiver
    }

}
