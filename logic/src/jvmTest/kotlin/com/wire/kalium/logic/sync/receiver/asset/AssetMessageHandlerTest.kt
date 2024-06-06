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
package com.wire.kalium.logic.sync.receiver.asset

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.asset.ValidateAssetMimeTypeUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.message.hasValidData
import com.wire.kalium.logic.sync.receiver.conversation.message.hasValidRemoteData
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AssetMessageHandlerTest {

    @Test
    fun givenAValidNonRestrictedNewGenericAssetMessage_whenHandlingIt_isCorrectlyProcessedAndIsVisible() = runTest {
        // Given
        val assetMessage = COMPLETE_ASSET_MESSAGE
        val assetMessageContent = assetMessage.content as MessageContent.Asset
        val isFileSharingEnabled = FileSharingStatus.Value.EnabledAll
        val (arrangement, assetMessageHandler) = Arrangement()
            .withSuccessfulFileSharingFlag(isFileSharingEnabled)
            .withSuccessfulStoredMessage(null)
            .withSuccessfulPersistMessageUseCase(assetMessage)
            .arrange()

        // When
        assetMessageHandler.handle(assetMessage)

        // Then
        assertTrue(assetMessageContent.value.hasValidRemoteData())
        coVerify {
            arrangement.persistMessage.invoke(matches {
                it.id == assetMessage.id
                        && it.conversationId.toString() == assetMessage.conversationId.toString()
                        && it.visibility == Message.Visibility.VISIBLE
            })
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.messageRepository.getMessageById(eq(assetMessage.conversationId), eq(assetMessage.id))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAValidPreviewNewGenericAssetMessage_whenHandlingIt_isCorrectlyProcessedAndIsNotVisible() = runTest {
        // Given
        val assetMessage = PREVIEW_ASSET_MESSAGE
        val isFileSharingEnabled = FileSharingStatus.Value.EnabledAll
        val (arrangement, assetMessageHandler) = Arrangement()
            .withSuccessfulFileSharingFlag(isFileSharingEnabled)
            .withSuccessfulStoredMessage(null)
            .withSuccessfulPersistMessageUseCase(assetMessage)
            .arrange()

        // When
        assetMessageHandler.handle(assetMessage)

        // Then
        coVerify {
            arrangement.persistMessage.invoke(matches {
                it.id == assetMessage.id
                        && it.conversationId.toString() == assetMessage.conversationId.toString()
                        && it.visibility == Message.Visibility.HIDDEN
            })
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.messageRepository.getMessageById(eq(assetMessage.conversationId), eq(assetMessage.id))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAValidPreviewNewImageAssetMessage_whenHandlingIt_isCorrectlyProcessedAndItIsVisible() = runTest {
        // Given
        val assetMessage = COMPLETE_ASSET_MESSAGE.copy(
            content = PREVIEW_ASSET_CONTENT.copy(
                value = PREVIEW_ASSET_CONTENT.value.copy(
                    name = "some-image.jpg",
                    mimeType = "image/jpg",
                    metadata = AssetContent.AssetMetadata.Image(100, 100)
                )
            )
        )
        val isFileSharingEnabled = FileSharingStatus.Value.EnabledAll
        val (arrangement, assetMessageHandler) = Arrangement()
            .withSuccessfulFileSharingFlag(isFileSharingEnabled)
            .withSuccessfulStoredMessage(null)
            .withSuccessfulPersistMessageUseCase(assetMessage)
            .arrange()

        // When
        assetMessageHandler.handle(assetMessage)

        // Then
        coVerify {
            arrangement.persistMessage.invoke(matches {
                it.id == assetMessage.id
                        && it.conversationId.toString() == assetMessage.conversationId.toString()
                        && it.visibility == Message.Visibility.VISIBLE
            })
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.messageRepository.getMessageById(eq(assetMessage.conversationId), eq(assetMessage.id))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenValidPreviewAssetMessageStoredAndItsAssetUpdate_whenHandlingTheUpdate_itIsCorrectlyProcessedAndVisible() = runTest {
        // Given
        val previewAssetMessage = PREVIEW_ASSET_MESSAGE.copy(visibility = Message.Visibility.HIDDEN)
        val updateAssetMessage = COMPLETE_ASSET_MESSAGE
        val isFileSharingEnabled = FileSharingStatus.Value.EnabledAll
        val (arrangement, assetMessageHandler) = Arrangement()
            .withSuccessfulFileSharingFlag(isFileSharingEnabled)
            .withSuccessfulStoredMessage(previewAssetMessage)
            .withSuccessfulPersistMessageUseCase(updateAssetMessage)
            .arrange()

        // When
        assetMessageHandler.handle(updateAssetMessage)

        // Then
        assertFalse((previewAssetMessage.content as MessageContent.Asset).value.hasValidRemoteData())
        assertTrue((updateAssetMessage.content as MessageContent.Asset).value.remoteData.hasValidData())
        coVerify {
            arrangement.persistMessage.invoke(matches {
                it.id == updateAssetMessage.id
                        && it.conversationId.toString() == updateAssetMessage.conversationId.toString()
                        && it.visibility == Message.Visibility.VISIBLE
            })
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.messageRepository.getMessageById(eq(previewAssetMessage.conversationId), eq(previewAssetMessage.id))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenValidPreviewAssetMessageStored_whenHandlingTheUpdateWithWrongEncryptionKeys_itIsProcessedButNoVisible() = runTest {
        // Given
        val previewAssetMessage = PREVIEW_ASSET_MESSAGE.copy(visibility = Message.Visibility.HIDDEN)
        val updateBrokenKeysAssetMessage = COMPLETE_ASSET_MESSAGE.copy(
            content = COMPLETE_ASSET_CONTENT.copy(
                value = COMPLETE_ASSET_CONTENT.value.copy(
                    remoteData = COMPLETE_ASSET_CONTENT.value.remoteData.copy(otrKey = byteArrayOf())
                )
            )
        )
        val isFileSharingEnabled = FileSharingStatus.Value.EnabledAll
        val (arrangement, assetMessageHandler) = Arrangement()
            .withSuccessfulFileSharingFlag(isFileSharingEnabled)
            .withSuccessfulStoredMessage(previewAssetMessage)
            .withSuccessfulPersistMessageUseCase(updateBrokenKeysAssetMessage)
            .arrange()

        // When
        assetMessageHandler.handle(updateBrokenKeysAssetMessage)

        // Then
        assertFalse((previewAssetMessage.content as MessageContent.Asset).value.hasValidRemoteData())
        assertFalse((updateBrokenKeysAssetMessage.content as MessageContent.Asset).value.remoteData.hasValidData())
        coVerify {
            arrangement.persistMessage.invoke(matches {
                it.id == updateBrokenKeysAssetMessage.id
                        && it.conversationId.toString() == updateBrokenKeysAssetMessage.conversationId.toString()
                        && it.visibility == Message.Visibility.HIDDEN
            })
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.messageRepository.getMessageById(eq(previewAssetMessage.conversationId), eq(previewAssetMessage.id))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenValidPreviewAssetMessageStored_whenHandlingTheUpdateWithImpostorSenderId_itIsProcessedButNoVisible() = runTest {
        // Given
        val previewAssetMessage = PREVIEW_ASSET_MESSAGE.copy(visibility = Message.Visibility.HIDDEN)
        val updateInvalidSenderIdAssetMessage = COMPLETE_ASSET_MESSAGE.copy(
            senderUserId = UserId("some-impostor-id", "some.domain.com")
        )
        val isFileSharingEnabled = FileSharingStatus.Value.EnabledAll
        val (arrangement, assetMessageHandler) = Arrangement()
            .withSuccessfulFileSharingFlag(isFileSharingEnabled)
            .withSuccessfulStoredMessage(previewAssetMessage)
            .withSuccessfulPersistMessageUseCase(updateInvalidSenderIdAssetMessage)
            .arrange()

        // When
        assetMessageHandler.handle(updateInvalidSenderIdAssetMessage)

        // Then
        assertFalse((previewAssetMessage.content as MessageContent.Asset).value.hasValidRemoteData())
        assertTrue((updateInvalidSenderIdAssetMessage.content as MessageContent.Asset).value.remoteData.hasValidData())
        coVerify {
            arrangement.persistMessage.invoke(matches {
                it.id == updateInvalidSenderIdAssetMessage.id
                        && it.conversationId.toString() == updateInvalidSenderIdAssetMessage.conversationId.toString()
                        && it.visibility == Message.Visibility.HIDDEN
            })
        }.wasNotInvoked()

        coVerify {
            arrangement.messageRepository.getMessageById(eq(previewAssetMessage.conversationId), eq(previewAssetMessage.id))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val messageRepository = mock(MessageRepository::class)

        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        @Mock
        val validateAssetMimeType = mock(ValidateAssetMimeTypeUseCase::class)

        private val assetMessageHandlerImpl =
            AssetMessageHandlerImpl(messageRepository, persistMessage, userConfigRepository, validateAssetMimeType)

        fun withValidateAssetMime(result: Boolean) = apply {
            every {
                validateAssetMimeType.invoke(any(), any())
            }.returns(result)
        }

        fun withSuccessfulFileSharingFlag(value: FileSharingStatus.Value) = apply {
            every {
                userConfigRepository.isFileSharingEnabled()
            }.returns(Either.Right(FileSharingStatus(state = value, isStatusChanged = false)))
        }

        suspend fun withSuccessfulPersistMessageUseCase(message: Message) = apply {
            coEvery {
                persistMessage(matches {
                    it.id == message.id && it.conversationId == message.conversationId
                })
            }.returns(Either.Right(Unit))
        }

        suspend fun withSuccessfulStoredMessage(persistedMessage: Message?) = apply {
            persistedMessage?.let { message ->
                coEvery {
                    messageRepository.getMessageById(any(), any())
                }.returns(Either.Right(message))
            } ?: coEvery {
                messageRepository.getMessageById(any(), any())
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        fun arrange() = this to assetMessageHandlerImpl
    }

    private companion object {
        val COMPLETE_ASSET_CONTENT = MessageContent.Asset(
            AssetContent(
                sizeInBytes = 100,
                name = "some-asset.zip",
                mimeType = "application/zip",
                metadata = null,
                remoteData = AssetContent.RemoteData(
                    otrKey = "otrKey".toByteArray(),
                    sha256 = "sha256".toByteArray(),
                    assetId = "some-asset-id",
                    assetDomain = "some-asset-domain",
                    assetToken = "some-asset-token",
                    encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM
                )
            )

        )
        val PREVIEW_ASSET_CONTENT = MessageContent.Asset(
            AssetContent(
                sizeInBytes = 100,
                name = "some-asset.zip",
                mimeType = "application/zip",
                metadata = null,
                remoteData = AssetContent.RemoteData(
                    otrKey = byteArrayOf(),
                    sha256 = byteArrayOf(),
                    assetId = "",
                    assetDomain = "",
                    assetToken = "",
                    encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM
                )
            )

        )
        val COMPLETE_ASSET_MESSAGE = Message.Regular(
            id = "uid-complete",
            content = COMPLETE_ASSET_CONTENT,
            conversationId = ConversationId("some-value", "some-domain.com"),
            date = "1970-01-01T00:00:01.000Z",
            senderUserId = UserId("some-sender-value", "some-sender-domain.com"),
            senderClientId = ClientId("some-client-value"),
            status = Message.Status.Sent,
            editStatus = Message.EditStatus.NotEdited,
            isSelfMessage = false
        )
        val PREVIEW_ASSET_MESSAGE = COMPLETE_ASSET_MESSAGE.copy(
            content = PREVIEW_ASSET_CONTENT,
            date = "1970-01-01T00:00:00.000Z",
        )
    }
}
