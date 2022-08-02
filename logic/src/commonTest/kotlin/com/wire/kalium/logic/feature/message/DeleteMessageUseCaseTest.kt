package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteMessageUseCaseTest {
    @Test
    fun givenASentMessage_WhenDeleteForEveryIsTrue_TheGeneratedMessageShouldBeCorrect() = runTest {
        // given
        val deleteForEveryone = true

        val (arrangement, deleteMessageUseCase) = Arrangement()
            .withSendMessageSucceed()
            .withSelfUser(TestUser.SELF)
            .withCurrentClientIdIs(SELF_CLIENT_ID)
            .withMessageRepositoryMarkMessageAsDeletedSucceed()
            .withMessageByStatus(Message.Status.SENT)
            .arrange()

        // when
        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).shouldSucceed()

        // then
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(
                matching { message ->
                    message.conversationId == TEST_CONVERSATION_ID && message.content == deletedMessageContent
                }
            )
            .wasInvoked(exactly = once)
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessageAsDeleted)
            .with(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAFailedMessage_WhenItGetsDeletedForEveryone_TheMessageShouldBeDeleted() = runTest {
        // given
        val deleteForEveryone = true
        val (arrangement, deleteMessageUseCase) = Arrangement()
            .withSendMessageSucceed()
            .withSelfUser(TestUser.SELF)
            .withCurrentClientIdIs(SELF_CLIENT_ID)
            .withMessageRepositoryMarkMessageAsDeletedSucceed()
            .withMessageRepositoryDeleteMessageSucceed()
            .withMessageByStatus(Message.Status.FAILED)
            .arrange()

        // when
        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).shouldSucceed()

        // then
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(anything())
            .wasNotInvoked()
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessageAsDeleted)
            .with(anything(), anything())
            .wasNotInvoked()
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::deleteMessage)
            .with(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenASentMessage_WhenDeleteForEveryoneIsFalse_TheGeneratedMessageShouldBeDeletedOnlyLocally() = runTest {
        // given
        val deleteForEveryone = false
        val (arrangement, deleteMessageUseCase) = Arrangement()
            .withSendMessageSucceed()
            .withSelfUser(TestUser.SELF)
            .withCurrentClientIdIs(SELF_CLIENT_ID)
            .withMessageRepositoryMarkMessageAsDeletedSucceed()
            .withMessageByStatus(Message.Status.SENT)
            .arrange()

        // when
        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).shouldSucceed()

        val deletedForMeContent = MessageContent.DeleteForMe(
            TEST_MESSAGE_UUID, TEST_CONVERSATION_ID.value,
            arrangement.idMapper.toProtoModel(
                TEST_CONVERSATION_ID
            )
        )

        // then
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(
                matching { message ->
                    message.conversationId == TestUser.SELF.id && message.content == deletedForMeContent
                }
            )
            .wasInvoked(exactly = once)

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessageAsDeleted)
            .with(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
            .wasInvoked(exactly = once)

    }

    @Test
    fun givenAMessageWithAsset_WhenDelete_TheDeleteAssetShouldBeInvoked() = runTest {
        // given
        val (arrangement, deleteMessageUseCase) = Arrangement()
            .withSendMessageSucceed()
            .withSelfUser(TestUser.SELF)
            .withCurrentClientIdIs(SELF_CLIENT_ID)
            .withMessageRepositoryMarkMessageAsDeletedSucceed()
            .withMessageRepositoryDeleteMessageSucceed()
            .withAssetMessage()
            .withAssetRepositoryDeleteAssetSucceed()
            .arrange()

        // when
        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, false).shouldSucceed()
        val deletedForMeContent = MessageContent.DeleteForMe(
            TEST_MESSAGE_UUID, TEST_CONVERSATION_ID.value,
            arrangement.idMapper.toProtoModel(
                TEST_CONVERSATION_ID
            )
        )

        // then
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(matching { message ->
                message.conversationId == TestUser.SELF.id && message.content == deletedForMeContent
            })
            .wasInvoked(exactly = once)

        verify(arrangement.assetRepository)
            .suspendFunction(arrangement.assetRepository::deleteAsset)
            .with(eq(ASSET_ID), eq(ASSET_TOKEN))
            .wasInvoked(exactly = once)

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessageAsDeleted)
            .with(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
            .wasInvoked(exactly = once)
    }

    class Arrangement {

        @Mock
        val messageRepository: MessageRepository = mock(MessageRepository::class)

        @Mock
        val userRepository: UserRepository = mock(UserRepository::class)

        @Mock
        val clientRepository: ClientRepository = mock(ClientRepository::class)

        @Mock
        val messageSender: MessageSender = mock(MessageSender::class)

        @Mock
        val assetRepository: AssetRepository = mock(AssetRepository::class)


        val idMapper: IdMapper = IdMapperImpl()

        fun arrange() = this to DeleteMessageUseCase(
            messageRepository,
            userRepository,
            clientRepository,
            assetRepository,
            messageSender,
            idMapper
        )

        fun withSendMessageSucceed() = apply {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withSelfUser(selfUser: SelfUser) = apply {
            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .thenReturn(flowOf(selfUser))
        }

        fun withCurrentClientIdIs(clientId: ClientId) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::currentClientId)
                .whenInvoked()
                .then { Either.Right(clientId) }
        }

        fun withMessageRepositoryMarkMessageAsDeletedSucceed() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::markMessageAsDeleted)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withMessageRepositoryDeleteMessageSucceed() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::deleteMessage)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withMessageByStatus(status: Message.Status) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(TestMessage.TEXT_MESSAGE.copy(status = status)))
        }

        fun withAssetMessage() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(TestMessage.TEXT_MESSAGE.copy(content = MESSAGE_ASSET_CONTENT)))
        }

        fun withAssetRepositoryDeleteAssetSucceed() = apply {
            given(assetRepository)
                .suspendFunction(assetRepository::deleteAsset)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }


    }

    companion object {
        val TEST_CONVERSATION_ID = TestConversation.ID
        const val TEST_MESSAGE_UUID = "messageUuid"
        val SELF_CLIENT_ID: ClientId = PlainId("client_self")
        val deletedMessageContent = MessageContent.DeleteMessage(TEST_MESSAGE_UUID)
        val ASSET_ID = AssetId("asset-id", "some-asset-domain.com")
        const val ASSET_TOKEN = "==some-asset-token"
        private val DUMMY_ASSET_REMOTE_DATA = AssetContent.RemoteData(
            otrKey = ByteArray(0),
            sha256 = ByteArray(16),
            assetId = ASSET_ID.value,
            assetToken = ASSET_TOKEN,
            assetDomain = ASSET_ID.domain,
            encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM
        )
        val MESSAGE_ASSET_CONTENT = MessageContent.Asset(
            AssetContent(
                remoteData = DUMMY_ASSET_REMOTE_DATA,
                sizeInBytes = 0,
                downloadStatus = Message.DownloadStatus.SAVED_EXTERNALLY,
                mimeType = "image/jpeg"
            ),
        )
        val TEST_MESSAGE = Message.Regular(
            id = TEST_MESSAGE_UUID,
            content = MessageContent.Text("some text"),
            conversationId = ConversationId("convo-id", "convo.domain"),
            date = "some-date",
            senderUserId = UserId("user-id", "domain"),
            senderClientId = ClientId("client-id"),
            status = Message.Status.SENT,
            editStatus = Message.EditStatus.NotEdited
        )
    }
}
