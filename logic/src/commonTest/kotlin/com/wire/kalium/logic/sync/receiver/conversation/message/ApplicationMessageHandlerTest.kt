package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.conversation.ClearConversationContentImpl
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.message.ClearConversationContentHandler
import com.wire.kalium.logic.sync.receiver.message.DeleteForMeHandler
import com.wire.kalium.logic.sync.receiver.message.LastReadContentHandler
import com.wire.kalium.logic.sync.receiver.message.MessageTextEditHandler
import com.wire.kalium.logic.util.Base64
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ApplicationMessageHandlerTest {

    @Test
    fun givenValidNewImageMessageEvent_whenHandling_shouldSetDownloadStatusAsInProgress() = runTest {
        val messageId = "messageId"
        val validImageContent = MessageContent.Asset(
            AssetContent(
                1000, "some-image.jpg", "image/jpg", AssetContent.AssetMetadata.Image(200, 200),
                AssetContent.RemoteData(
                    ByteArray(16), ByteArray(16), "assetid", null, null, null
                ),
                Message.UploadStatus.NOT_UPLOADED, Message.DownloadStatus.NOT_DOWNLOADED
            )
        )
        val protoContent = ProtoContent.Readable(messageId, validImageContent)
        val coreFailure = StorageFailure.DataNotFound
        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFileSharingEnabled()
            .withErrorGetMessageById(coreFailure)
            .arrange()

        val encodedEncryptedContent = Base64.encodeToBase64("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent.decodeToString())
        messageHandler.handleContent(
            messageEvent.conversationId,
            messageEvent.timestampIso,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

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

    private class Arrangement {
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
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        @Mock
        private val callManager = mock(classOf<CallManager>())

        @Mock
        val persistReactionsUseCase = mock(classOf<PersistReactionUseCase>())

        private val applicationMessageHandler = ApplicationMessageHandlerImpl(
            userRepository,
            assetRepository,
            messageRepository,
            userConfigRepository,
            lazyOf(callManager),
            persistMessage,
            persistReactionsUseCase,
            // TODO(Refactor): Test smaller handlers on their own Test class
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
            )
        )

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
                .thenReturn(
                    Either.Right(
                        FileSharingStatus(
                            isFileSharingEnabled = true,
                            isStatusChanged = false
                        )
                    )
                )
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

        fun withGetConversation(conversation: Conversation? = TestConversation.CONVERSATION) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationById)
                .whenInvokedWith(any())
                .thenReturn(conversation)
        }

        fun arrange() = this to applicationMessageHandler

    }
}
