/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.asset.ScheduleNewAssetMessageUseCaseTest
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageUploadStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateUploadStatusResult
import com.wire.kalium.logic.framework.TestAsset.mockedLongAssetData
import com.wire.kalium.logic.framework.TestMessage.ASSET_CONTENT
import com.wire.kalium.logic.framework.TestMessage.TEST_DATE_STRING
import com.wire.kalium.logic.framework.TestMessage.TEXT_MESSAGE
import com.wire.kalium.logic.framework.TestMessage.assetMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.persistence.dao.message.MessageEntity
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class RetryFailedMessageUseCaseTest {

    private fun testResendingWithGivenMessageStatus(status: Message.Status, shouldSucceed: Boolean) =
        runTest(testDispatcher.default) {
            // given
            val message = TEXT_MESSAGE.copy(status = status)
            val (_, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withSendMessage(Either.Right(Unit))
                .arrange()

            // when
            val result = useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            if (shouldSucceed) assertIs<Either.Right<Unit>>(result)
            else assertIs<Either.Left<CoreFailure>>(result)
        }

    @Test
    fun givenAFailedMessage_whenRetryingFailedMessage_thenShouldReturnSuccess() =
        testResendingWithGivenMessageStatus(Message.Status.Failed, true)

    @Test
    fun givenAFailedRemotelyMessage_whenRetryingFailedMessage_thenShouldReturnSuccess() =
        testResendingWithGivenMessageStatus(Message.Status.FailedRemotely, true)

    @Test
    fun givenASentMessage_whenRetryingFailedMessage_thenShouldReturnFailure() =
        testResendingWithGivenMessageStatus(Message.Status.Sent, false)

    @Test
    fun givenAReadMessage_whenRetryingFailedMessage_thenShouldReturnFailure() =
        testResendingWithGivenMessageStatus(Message.Status.Read(1), false)

    @Test
    fun givenAPendingMessage_whenRetryingFailedMessage_thenShouldReturnFailure() =
        testResendingWithGivenMessageStatus(Message.Status.Pending, false)

    @Test
    fun givenAValidFailedMessage_whenRetryingFailedMessage_thenShouldSendAMessage() =
        runTest(testDispatcher.default) {
            // given
            val message = TEXT_MESSAGE.copy(status = Message.Status.Failed)
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withSendMessage(Either.Right(Unit))
                .arrange()

            // when
            useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.PENDING), eq(message.conversationId), eq(message.id))
                .wasInvoked(exactly = once)
            verify(arrangement.messageSender)
                .suspendFunction(arrangement.messageSender::sendMessage)
                .with(eq(message), anything())
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenAValidFailedEditedMessage_whenRetryingFailedMessage_thenShouldSendAsSignalingWithNewId() =
        runTest(testDispatcher.default) {
            // given
            val message = TEXT_MESSAGE.copy(status = Message.Status.Failed, editStatus = Message.EditStatus.Edited(TEST_DATE_STRING))
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withSendMessage(Either.Right(Unit))
                .arrange()

            // when
            useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            verify(arrangement.messageSender)
                .suspendFunction(arrangement.messageSender::sendMessage)
                .with(
                    matching {
                        it is Message.Signaling // message edits are sent as signaling messages
                                && it.id != message.id // when editing we need to generate and set a new id
                                && it.content is MessageContent.TextEdited
                                && (it.content as MessageContent.TextEdited).editMessageId == message.id // original id in edited content
                    }, anything()
                )
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenAValidFailedAndNotUploadedAssetMessage_whenRetryingFailedMessage_thenUploadAssetAndSendAMessageWithProperAssetRemoteData() =
        runTest(testDispatcher.default) {
            // given
            val name = "some_asset.txt"
            val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name, uploadStatus = Message.UploadStatus.FAILED_UPLOAD))
            val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
            val message = assetMessage().copy(content = content, status = Message.Status.Failed)
            val uploadedAssetId = UploadedAssetId("remote_key", "remote_domain", "remote_token")
            val uploadedAssetSha = SHA256Key(byteArrayOf())
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withUpdateAssetMessageUploadStatus(UpdateUploadStatusResult.Success)
                .withFetchPrivateDecodedAsset(Either.Right(path))
                .withStoredData(mockedLongAssetData(), path)
                .withUploadAndPersistPrivateAsset(Either.Right(uploadedAssetId to uploadedAssetSha))
                .withPersistMessage(Either.Right(Unit))
                .withSendMessage(Either.Right(Unit))
                .arrange()

            // when
            useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            verify(arrangement.assetRepository)
                .suspendFunction(arrangement.assetRepository::uploadAndPersistPrivateAsset)
                .with(anything(), eq(path), anything(), eq(name.fileExtension()))
                .wasInvoked(exactly = once)
            verify(arrangement.messageSender)
                .suspendFunction(arrangement.messageSender::sendMessage)
                .with(matching {
                    it.id == message.id && it.content is MessageContent.Asset
                            && (it.content as MessageContent.Asset).value.remoteData.assetId == uploadedAssetId.key
                            && (it.content as MessageContent.Asset).value.remoteData.assetDomain == uploadedAssetId.domain
                            && (it.content as MessageContent.Asset).value.remoteData.assetToken == uploadedAssetId.assetToken
                }, anything())
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenAValidFailedAssetMessageWithAssetAlreadyUploaded_whenRetryingFailedMessage_thenDoNotUploadAssetAndSendAMessage() =
        runTest(testDispatcher.default) {
            // given
            val name = "some_asset.txt"
            val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name, uploadStatus = Message.UploadStatus.UPLOADED))
            val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
            val message = assetMessage().copy(content = content, status = Message.Status.Failed)
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withUpdateAssetMessageUploadStatus(UpdateUploadStatusResult.Success)
                .withFetchPrivateDecodedAsset(Either.Right(path))
                .withStoredData(mockedLongAssetData(), path)
                .withPersistMessage(Either.Right(Unit))
                .withSendMessage(Either.Right(Unit))
                .arrange()

            // when
            useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            verify(arrangement.assetRepository)
                .suspendFunction(arrangement.assetRepository::uploadAndPersistPrivateAsset)
                .with(anything(), anything(), anything(), anything())
                .wasNotInvoked()
            verify(arrangement.messageSender)
                .suspendFunction(arrangement.messageSender::sendMessage)
                .with(eq(message), anything())
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenSendingMessageReturnsFailure_whenRetryingFailedMessage_thenShouldStillReturnSuccess() =
        runTest(testDispatcher.default) {
            // given
            val message = TEXT_MESSAGE.copy(status = Message.Status.Failed)
            val (_, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withSendMessage(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)))
                .arrange()

            // when
            val result = useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            assertIs<Either.Right<Unit>>(result)
        }

    @Test
    fun givenSendingMessageReturnsFailure_whenRetryingFailedRemotelyMessage_thenShouldStillReturnSuccess() =
        runTest(testDispatcher.default) {
            // given
            val message = TEXT_MESSAGE.copy(status = Message.Status.FailedRemotely)
            val (_, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withSendMessage(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)))
                .arrange()

            // when
            val result = useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            assertIs<Either.Right<Unit>>(result)
        }

    @Test
    fun givenUploadingAssetReturnsFailure_whenRetryingFailedMessage_thenShouldStillReturnSuccess() =
        runTest(testDispatcher.default) {
            // given
            val name = "some_asset.txt"
            val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name, uploadStatus = Message.UploadStatus.FAILED_UPLOAD))
            val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
            val message = assetMessage().copy(content = content, status = Message.Status.Failed)
            val (_, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withUpdateAssetMessageUploadStatus(UpdateUploadStatusResult.Success)
                .withFetchPrivateDecodedAsset(Either.Right(path))
                .withStoredData(mockedLongAssetData(), path)
                .withUploadAndPersistPrivateAsset(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)))
                .arrange()

            // when
            val result = useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            assertIs<Either.Right<Unit>>(result)
        }

    @Test
    fun givenUploadingAssetIsSuccessfulButSendingAssetMessageReturnsFailure_whenRetryingFailedMessage_thenPersistTheAssetRemoteData() =
        runTest(testDispatcher.default) {
            // given
            val name = "some_asset.txt"
            val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name, uploadStatus = Message.UploadStatus.FAILED_UPLOAD))
            val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
            val message = assetMessage().copy(content = content, status = Message.Status.Failed)
            val uploadedAssetId = UploadedAssetId("remote_key", "remote_domain", "remote_token")
            val uploadedAssetSha = SHA256Key(byteArrayOf())
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withUpdateAssetMessageUploadStatus(UpdateUploadStatusResult.Success)
                .withFetchPrivateDecodedAsset(Either.Right(path))
                .withStoredData(mockedLongAssetData(), path)
                .withUploadAndPersistPrivateAsset(Either.Right(uploadedAssetId to uploadedAssetSha))
                .withPersistMessage(Either.Right(Unit))
                .withSendMessage(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)))
                .arrange()

            // when
            useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(
                    matching {
                        it.id == message.id && it.content is MessageContent.Asset
                                && (it.content as MessageContent.Asset).value.remoteData.assetId == uploadedAssetId.key
                                && (it.content as MessageContent.Asset).value.remoteData.assetDomain == uploadedAssetId.domain
                                && (it.content as MessageContent.Asset).value.remoteData.assetToken == uploadedAssetId.assetToken
                    }
                )
                .wasInvoked(exactly = once)
        }

    private class Arrangement {

        @Mock
        val messageRepository = mock(MessageRepository::class)

        @Mock
        val assetRepository = mock(AssetRepository::class)

        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        @Mock
        val messageSender = mock(MessageSender::class)

        @Mock
        val updateAssetMessageUploadStatus = mock(UpdateAssetMessageUploadStatusUseCase::class)

        @Mock
        val messageSendFailureHandler = configure(mock(MessageSendFailureHandler::class)) { stubsUnitByDefault = true }

        private val testScope = TestScope(testDispatcher.default)

        fun withGetMessageById(result: Either<StorageFailure, Message>): Arrangement = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withUpdateMessageStatus(result: Either<CoreFailure, Unit>): Arrangement = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withUpdateAssetMessageUploadStatus(result: UpdateUploadStatusResult): Arrangement = apply {
            given(updateAssetMessageUploadStatus)
                .suspendFunction(updateAssetMessageUploadStatus::invoke)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(result)
        }

        fun withFetchPrivateDecodedAsset(result: Either<CoreFailure, Path>): Arrangement = apply {
            given(assetRepository)
                .suspendFunction(assetRepository::fetchPrivateDecodedAsset)
                .whenInvokedWith(anything(), anything(), anything(), anything(), anything(), anything(), anything())
                .thenReturn(result)
        }

        fun withUploadAndPersistPrivateAsset(result: Either<CoreFailure, Pair<UploadedAssetId, SHA256Key>>): Arrangement = apply {
            given(assetRepository)
                .suspendFunction(assetRepository::uploadAndPersistPrivateAsset)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(result)
        }

        fun withPersistMessage(result: Either<CoreFailure, Unit>): Arrangement = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(anything())
                .thenReturn(result)
        }

        fun withSendMessage(result: Either<CoreFailure, Unit>): Arrangement = apply {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withStoredData(data: ByteArray, dataPath: Path): Arrangement = apply {
            ScheduleNewAssetMessageUseCaseTest.fakeKaliumFileSystem.sink(dataPath).buffer().use {
                it.write(data)
                it.flush()
            }
        }

        fun arrange() = this to RetryFailedMessageUseCase(
            messageRepository,
            assetRepository,
            persistMessage,
            testScope,
            testDispatcher,
            messageSender,
            updateAssetMessageUploadStatus,
            messageSendFailureHandler
        )
    }

    companion object {
        val fakeKaliumFileSystem = FakeKaliumFileSystem()
        val testDispatcher = TestKaliumDispatcher
    }
}
