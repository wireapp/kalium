/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.cells.domain.usecase.DeleteMessageAttachmentsUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class DeleteMessageUseCaseTest {
    @Test
    fun givenASentMessage_WhenDeleteForEveryIsTrue_TheGeneratedMessageShouldBeCorrect() = runTest {
        val deleteForEveryone = true

        val (arrangement, deleteMessageUseCase) = Arrangement(testKaliumDispatcher)
            .withSendMessageSucceed()
            .withSelfUser(TestUser.SELF)
            .withCurrentClientId(SELF_CLIENT_ID)
            .withSelfConversationIds(listOf(SELF_CONVERSATION_ID))
            .withCompletedSlowSync()
            .withMessageRepositoryMarkMessageAsDeletedSucceed()
            .withTextMessage(Message.Status.Sent)
            .arrange()

        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).toEither().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                matching { message ->
                    message.conversationId == TEST_CONVERSATION_ID && message.content == deletedMessageContent
                },
                any()
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.markMessageAsDeleted(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
        }
    }

    @Test
    fun givenAFailedMessage_WhenItGetsDeletedForEveryone_TheMessageShouldBeDeleted() = runTest {
        val deleteForEveryone = true
        val (arrangement, deleteMessageUseCase) = Arrangement(testKaliumDispatcher)
            .withSendMessageSucceed()
            .withSelfUser(TestUser.SELF)
            .withCurrentClientId(SELF_CLIENT_ID)
            .withSelfConversationIds(listOf(SELF_CONVERSATION_ID))
            .withCompletedSlowSync()
            .withMessageRepositoryMarkMessageAsDeletedSucceed()
            .withMessageRepositoryDeleteMessageSucceed()
            .withTextMessage(Message.Status.Failed)
            .arrange()

        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).toEither().shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendMessage(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.markMessageAsDeleted(any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.deleteMessage(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
        }
    }

    @Test
    fun givenASentMessage_WhenDeleteForEveryoneIsFalse_TheGeneratedMessageShouldBeDeletedOnlyLocally() = runTest {
        val deleteForEveryone = false
        val (arrangement, deleteMessageUseCase) = Arrangement(testKaliumDispatcher)
            .withSendMessageSucceed()
            .withSelfUser(TestUser.SELF)
            .withCurrentClientId(SELF_CLIENT_ID)
            .withSelfConversationIds(listOf(SELF_CONVERSATION_ID))
            .withCompletedSlowSync()
            .withMessageRepositoryMarkMessageAsDeletedSucceed()
            .withTextMessage(Message.Status.Sent)
            .arrange()

        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).toEither().shouldSucceed()

        val deletedForMeContent = MessageContent.DeleteForMe(
            TEST_MESSAGE_UUID,
            TEST_CONVERSATION_ID
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                matching { message ->
                    message.conversationId == SELF_CONVERSATION_ID && message.content == deletedForMeContent
                },
                any()
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.markMessageAsDeleted(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
        }

    }

    @Test
    fun givenAMessageWithAsset_WhenDelete_TheDeleteAssetShouldBeInvoked() = runTest {
        val (arrangement, deleteMessageUseCase) = Arrangement(testKaliumDispatcher)
            .withSendMessageSucceed()
            .withSelfUser(TestUser.SELF)
            .withCurrentClientId(SELF_CLIENT_ID)
            .withSelfConversationIds(listOf(SELF_CONVERSATION_ID))
            .withCompletedSlowSync()
            .withMessageRepositoryMarkMessageAsDeletedSucceed()
            .withMessageRepositoryDeleteMessageSucceed()
            .withAssetMessage()
            .withAssetRepositoryDeleteAssetSucceed()
            .arrange()

        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, false).toEither().shouldSucceed()
        val deletedForMeContent = MessageContent.DeleteForMe(
            TEST_MESSAGE_UUID,
            TEST_CONVERSATION_ID
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                matching { message ->
                    message.conversationId == SELF_CONVERSATION_ID && message.content == deletedForMeContent
                },
                any()
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.deleteAsset(eq(ASSET_ID.value), eq(ASSET_ID.domain), eq(ASSET_TOKEN))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.markMessageAsDeleted(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
        }
    }

    @Test
    fun givenAEphemeralSentMessage_WhenDeleteForEveryIsTrue_TheGeneratedMessageShouldBeCorrect() = runTest {
        val deleteForEveryone = true

        val (arrangement, deleteMessageUseCase) = Arrangement(testKaliumDispatcher)
            .withSendMessageSucceed()
            .withSelfUser(TestUser.SELF)
            .withCurrentClientId(SELF_CLIENT_ID)
            .withSelfConversationIds(listOf(SELF_CONVERSATION_ID))
            .withCompletedSlowSync()
            .withMessageRepositoryDeletionSucceed()
            .withTextMessage(
                Message.Status.Sent,
                expirationData = Message.ExpirationData(
                    expireAfter = 10.seconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                )
            )
            .arrange()

        deleteMessageUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID, deleteForEveryone).toEither().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                matching { message ->
                    message.conversationId == TEST_CONVERSATION_ID && message.content == deletedMessageContent
                },
                any()
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.deleteMessage(eq(TEST_MESSAGE_UUID), eq(TEST_CONVERSATION_ID))
        }
    }

    private class Arrangement(var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {

        val currentClientIdProvider: CurrentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        val messageRepository: MessageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        val userRepository: UserRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        val slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)
        val messageSender: MessageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val assetRepository: AssetRepository = mock<AssetRepository>(mode = MockMode.autoUnit)
        val selfConversationIdProvider: SelfConversationIdProvider = mock<SelfConversationIdProvider>(mode = MockMode.autoUnit)
        val deleteCellAssets: DeleteMessageAttachmentsUseCase = mock<DeleteMessageAttachmentsUseCase>(mode = MockMode.autoUnit)

        val completeStateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()

        val persistenceEventHookNotifier: PersistenceEventHookNotifier = object : PersistenceEventHookNotifier {}

        fun arrange() = this to DeleteMessageUseCase(
            messageRepository,
            assetRepository,
            slowSyncRepository,
            messageSender,
            TestUser.SELF.id,
            currentClientIdProvider,
            selfConversationIdProvider,
            deleteCellAssets,
            persistenceEventHookNotifier,
            dispatcher
        )

        suspend fun withSendMessageSucceed() = apply {
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withSelfUser(selfUser: SelfUser) = apply {
            everySuspend {
                userRepository.observeSelfUser()
            } returns flowOf(selfUser)
        }

        suspend fun withCurrentClientId(clientId: ClientId) = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns Either.Right(clientId)
        }

        fun withCompletedSlowSync() = apply {
            every {
                slowSyncRepository.slowSyncStatus
            } returns completeStateFlow
        }

        suspend fun withMessageRepositoryMarkMessageAsDeletedSucceed() = apply {
            everySuspend {
                messageRepository.markMessageAsDeleted(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withMessageRepositoryDeletionSucceed() = apply {
            everySuspend {
                messageRepository.deleteMessage(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withMessageRepositoryDeleteMessageSucceed() = apply {
            everySuspend {
                messageRepository.deleteMessage(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withTextMessage(status: Message.Status, expirationData: Message.ExpirationData? = null) = apply {
            everySuspend {
                messageRepository.getMessageById(any(), any())
            } returns Either.Right(TestMessage.TEXT_MESSAGE.copy(status = status, expirationData = expirationData))
        }

        suspend fun withAssetMessage() = apply {
            everySuspend {
                messageRepository.getMessageById(any(), any())
            } returns Either.Right(TestMessage.TEXT_MESSAGE.copy(content = MESSAGE_ASSET_CONTENT))
        }

        suspend fun withAssetRepositoryDeleteAssetSucceed() = apply {
            everySuspend {
                assetRepository.deleteAsset(any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withSelfConversationIds(conversationIds: List<ConversationId>) = apply {
            everySuspend {
                selfConversationIdProvider.invoke()
            } returns Either.Right(conversationIds)
        }

    }

    companion object {
        val TEST_CONVERSATION_ID = TestConversation.ID
        val SELF_CONVERSATION_ID = TestConversation.SELF().id
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
                mimeType = "image/jpeg"
            ),
        )
    }
}
