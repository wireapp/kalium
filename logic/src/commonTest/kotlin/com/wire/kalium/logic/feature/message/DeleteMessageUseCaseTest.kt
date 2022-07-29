package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteMessageUseCaseTest {

    @Mock
    private val messageRepository: MessageRepository = mock(MessageRepository::class)

    @Mock
    val userRepository: UserRepository = mock(UserRepository::class)

    @Mock
    val assetRepository: AssetRepository = mock(AssetRepository::class)

    @Mock
    private val clientRepository: ClientRepository = mock(ClientRepository::class)

    val idMapper: IdMapper = IdMapperImpl()

    @Mock
    private val syncManager = configure(mock(SyncManager::class)) { stubsUnitByDefault = true }

    @Mock
    private val messageSender: MessageSender = mock(MessageSender::class)

    private lateinit var deleteMessageUseCase: DeleteMessageUseCase

    @BeforeTest
    fun setup() {
        deleteMessageUseCase = DeleteMessageUseCase(
            messageRepository,
            userRepository,
            clientRepository,
            assetRepository,
            syncManager,
            messageSender,
            idMapper
        )

        given(messageSender)
            .suspendFunction(messageSender::sendMessage)
            .whenInvokedWith(anything())
            .thenReturn(Either.Right(Unit))
        given(userRepository)
            .suspendFunction(userRepository::observeSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))
        given(clientRepository)
            .suspendFunction(clientRepository::currentClientId)
            .whenInvoked()
            .then { Either.Right(SELF_CLIENT_ID) }
        given(messageRepository)
            .suspendFunction(messageRepository::getMessageById)
            .whenInvokedWith(anything(), anything())
            .thenReturn(Either.Right(TEST_MESSAGE))
        given(messageRepository)
            .suspendFunction(messageRepository::markMessageAsDeleted)
            .whenInvokedWith(anything(), anything())
            .thenReturn(Either.Right(Unit))

    }

    @Test
    fun givenAMessage_WhenDeleteForEveryIsTrue_TheGeneratedMessageShouldBeCorrect() = runTest {
        // given
        val deleteForEveryone = true

        // when
        val result = deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).shouldSucceed()

        // then
        verify(messageSender)
            .suspendFunction(messageSender::sendMessage)
            .with(matching { message ->
                message.conversationId == TEST_CONVERSATION_ID && message.content == deletedMessageContent
            })
            .wasInvoked(exactly = once)
        verify(messageRepository)
            .suspendFunction(messageRepository::markMessageAsDeleted)
            .with(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAMessage_WhenDeleteForEveryIsFalse_TheGeneratedMessageShouldBeCorrect() = runTest {
        // given
        val deleteForEveryone = false

        // when
        val result = deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).shouldSucceed()
        val deletedForMeContent = MessageContent.DeleteForMe(
            TEST_MESSAGE_UUID, TEST_CONVERSATION_ID.value, idMapper.toProtoModel(
                TEST_CONVERSATION_ID
            )
        )

        // then
        verify(messageSender)
            .suspendFunction(messageSender::sendMessage)
            .with(matching { message ->
                message.conversationId == TestUser.SELF.id && message.content == deletedForMeContent
            })
            .wasInvoked(exactly = once)

        verify(messageRepository)
            .suspendFunction(messageRepository::markMessageAsDeleted)
            .with(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
            .wasInvoked(exactly = once)

    }

    @Test
    fun givenAMessageWithAsset_WhenDelete_TheDeleteAssetShouldBeInvoked() = runTest {
        // given
        given(messageRepository)
            .suspendFunction(messageRepository::getMessageById)
            .whenInvokedWith(anything(), anything())
            .thenReturn(Either.Right(TEST_MESSAGE.copy(content = MESSAGE_ASSET_CONTENT)))
        given(assetRepository)
            .suspendFunction(assetRepository::deleteAsset)
            .whenInvokedWith(anything(), anything())
            .thenReturn(Either.Right(Unit))

        // when
        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, false).shouldSucceed()
        val deletedForMeContent = MessageContent.DeleteForMe(
            TEST_MESSAGE_UUID, TEST_CONVERSATION_ID.value, idMapper.toProtoModel(
                TEST_CONVERSATION_ID
            )
        )

        // then
        verify(messageSender)
            .suspendFunction(messageSender::sendMessage)
            .with(matching { message ->
                message.conversationId == TestUser.SELF.id && message.content == deletedForMeContent
            })
            .wasInvoked(exactly = once)

        verify(assetRepository)
            .suspendFunction(assetRepository::deleteAsset)
            .with(eq(ASSET_ID), eq(ASSET_TOKEN))
            .wasInvoked(exactly = once)

        verify(messageRepository)
            .suspendFunction(messageRepository::markMessageAsDeleted)
            .with(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
            .wasInvoked(exactly = once)

    }


    @Test
    fun givenAMessage_whenDeleting_thenStartSyncIsInvoked() = runTest {
        // when
        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, true).shouldSucceed()

        // then
        verify(syncManager)
            .function(syncManager::startSyncIfIdle)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAMessage_whenDeleting_thenShouldNotWaitForAnyKindOfSyncState() = runTest {
        // when
        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, true).shouldSucceed()

        // then
        verify(syncManager)
            .suspendFunction(syncManager::waitUntilLive)
            .wasNotInvoked()
        verify(syncManager)
            .suspendFunction(syncManager::waitUntilSlowSyncCompletion)
            .wasNotInvoked()
    }

    companion object {
        val TEST_CONVERSATION_ID = TestConversation.ID
        const val TEST_MESSAGE_UUID = "messageUuid"
        const val TEST_TIME = "time"
        val TEST_CORE_FAILURE = Either.Left(CoreFailure.Unknown(Throwable("an error")))
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
