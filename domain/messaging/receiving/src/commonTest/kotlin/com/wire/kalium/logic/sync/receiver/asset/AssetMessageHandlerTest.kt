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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.FileSharingStatusProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.DeliveryStatus
import com.wire.kalium.logic.data.message.IncomingAssetMessageLookup
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.StoredIncomingAssetMessage
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class AssetMessageHandlerTest {

    @Test
    fun givenNonAssetInput_whenHandling_thenItReturnsBeforeConsultingAnyDependency() = runTest {
        val arrangement = Arrangement()

        arrangement.handler.handle(incomingCompleteMessage.copy(content = MessageContent.Text("not an asset")))

        assertEquals(emptyList(), arrangement.calls)
        assertEquals(emptyList(), arrangement.persistedMessages)
    }

    @Test
    fun givenFileSharingStatusLeft_whenHandling_thenAllLaterWorkIsSkipped() = runTest {
        val arrangement = Arrangement().apply {
            fileSharingResult = { Either.Left(StorageFailure.DataNotFound) }
        }

        arrangement.handler.handle(incomingCompleteMessage)

        assertEquals(listOf("fileSharing"), arrangement.calls)
    }

    @Test
    fun givenFileSharingStatusException_whenHandling_thenTheSameExceptionEscapes() = runTest {
        val expected = IllegalStateException("file-sharing lookup failed")
        val arrangement = Arrangement().apply { fileSharingResult = { throw expected } }

        val actual = assertFailsWith<IllegalStateException> { arrangement.handler.handle(incomingCompleteMessage) }

        assertSame(expected, actual)
        assertEquals(listOf("fileSharing"), arrangement.calls)
    }

    @Test
    fun givenFileSharingStatusCancellation_whenHandling_thenTheSameCancellationEscapes() = runTest {
        val expected = CancellationException("file-sharing lookup cancelled")
        val arrangement = Arrangement().apply { fileSharingResult = { throw expected } }

        val actual = assertFailsWith<CancellationException> { arrangement.handler.handle(incomingCompleteMessage) }

        assertSame(expected, actual)
        assertEquals(listOf("fileSharing"), arrangement.calls)
    }

    @Test
    fun givenFileSharingDisabled_whenHandling_thenRestrictedAssetIsPersistedImmediately() = runTest {
        val incoming = incomingCompleteMessage.copy(
            content = completeAssetContent.copy(value = completeAssetContent.value.copy(name = null))
        )
        val arrangement = Arrangement().withFileSharing(FileSharingStatus.Value.Disabled)

        arrangement.handler.handle(incoming)

        assertEquals(listOf("fileSharing", "persist"), arrangement.calls)
        assertEquals(
            listOf<Message.Standalone>(
                incoming.copy(
                    content = MessageContent.RestrictedAsset(
                        mimeType = completeAssetContent.value.mimeType,
                        sizeInBytes = completeAssetContent.value.sizeInBytes,
                        name = "",
                    )
                )
            ),
            arrangement.persistedMessages,
        )
        assertEquals(emptyList(), arrangement.validationArguments)
        assertEquals(emptyList(), arrangement.lookupArguments)
    }

    @Test
    fun givenFileSharingEnabledAllAndMissingCompleteAsset_whenHandling_thenValidationIsSkippedAndMessageIsVisible() = runTest {
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledAll)
            .withLookup(Either.Left(StorageFailure.DataNotFound))

        arrangement.handler.handle(incomingCompleteMessage)

        assertEquals(listOf("fileSharing", "lookup", "persist"), arrangement.calls)
        assertEquals(emptyList(), arrangement.validationArguments)
        assertEquals(
            listOf<Message.Standalone>(incomingCompleteMessage.copy(visibility = Message.Visibility.VISIBLE)),
            arrangement.persistedMessages,
        )
        assertEquals(listOf(incomingCompleteMessage.conversationId to incomingCompleteMessage.id), arrangement.lookupArguments)
    }

    @Test
    fun givenFileSharingEnabledAllAndMissingGenericPreview_whenHandling_thenMessageIsHidden() = runTest {
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledAll)
            .withLookup(Either.Left(StorageFailure.DataNotFound))

        arrangement.handler.handle(incomingGenericPreviewMessage)

        assertEquals(
            listOf<Message.Standalone>(incomingGenericPreviewMessage.copy(visibility = Message.Visibility.HIDDEN)),
            arrangement.persistedMessages,
        )
    }

    @Test
    fun givenFileSharingEnabledAllAndMissingImagePreview_whenHandling_thenMessageIsVisibleBecauseMetadataIsComplete() = runTest {
        val imagePreview = incomingGenericPreviewMessage.copy(
            content = previewAssetContent.copy(
                value = previewAssetContent.value.copy(
                    name = "preview.jpg",
                    mimeType = "image/jpeg",
                    metadata = AssetContent.AssetMetadata.Image(width = 100, height = 80),
                )
            )
        )
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledAll)
            .withLookup(Either.Left(StorageFailure.DataNotFound))

        arrangement.handler.handle(imagePreview)

        assertEquals(
            listOf<Message.Standalone>(imagePreview.copy(visibility = Message.Visibility.VISIBLE)),
            arrangement.persistedMessages,
        )
    }

    @Test
    fun givenEnabledSomeAndValidatorAllowsAsset_whenHandling_thenValidationPrecedesLookupAndPersistence() = runTest {
        val allowedTypes = listOf("zip", "txt")
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledSome(allowedTypes))
            .withValidation(true)
            .withLookup(Either.Left(StorageFailure.DataNotFound))

        arrangement.handler.handle(incomingCompleteMessage)

        assertEquals(listOf("fileSharing", "validate", "lookup", "persist"), arrangement.calls)
        assertEquals(
            listOf(ValidationArguments("archive.zip", "application/zip", allowedTypes)),
            arrangement.validationArguments,
        )
    }

    @Test
    fun givenEnabledSomeAndValidatorRejectsNamedAsset_whenHandling_thenItIsRestrictedWithoutLookup() = runTest {
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledSome(listOf("txt")))
            .withValidation(false)

        arrangement.handler.handle(incomingCompleteMessage)

        assertEquals(listOf("fileSharing", "validate", "persist"), arrangement.calls)
        assertEquals(emptyList(), arrangement.lookupArguments)
        assertIs<MessageContent.RestrictedAsset>(arrangement.persistedMessages.single().content)
    }

    @Test
    fun givenValidatorException_whenHandlingEnabledSome_thenTheSameExceptionEscapes() = runTest {
        val expected = IllegalStateException("validation failed")
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledSome(listOf("txt")))
            .apply { validationResult = { throw expected } }

        val actual = assertFailsWith<IllegalStateException> { arrangement.handler.handle(incomingCompleteMessage) }

        assertSame(expected, actual)
        assertEquals(listOf("fileSharing", "validate"), arrangement.calls)
    }

    @Test
    fun givenValidatorCancellation_whenHandlingEnabledSome_thenTheSameCancellationEscapes() = runTest {
        val expected = CancellationException("validation cancelled")
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledSome(listOf("txt")))
            .apply { validationResult = { throw expected } }

        val actual = assertFailsWith<CancellationException> { arrangement.handler.handle(incomingCompleteMessage) }

        assertSame(expected, actual)
        assertEquals(listOf("fileSharing", "validate"), arrangement.calls)
    }

    @Test
    fun givenEnabledSomeRejectedNullNameWithCompleteDataAndMissingStoredMessage_whenHandling_thenItIsRestrictedAfterLookup() =
        runTest {
            val incoming = incomingCompleteMessage.copy(
                content = completeAssetContent.copy(value = completeAssetContent.value.copy(name = null))
            )
            val arrangement = Arrangement()
                .withFileSharing(FileSharingStatus.Value.EnabledSome(listOf("txt")))
                .withValidation(false)
                .withLookup(Either.Left(StorageFailure.DataNotFound))

            arrangement.handler.handle(incoming)

            assertEquals(listOf("fileSharing", "validate", "lookup", "persist"), arrangement.calls)
            assertEquals(MessageContent.RestrictedAsset("application/zip", 100L, ""), arrangement.persistedMessages.single().content)
        }

    @Test
    fun givenEnabledSomeRejectedEmptyNameWithCompleteDataAndStoredAsset_whenHandling_thenItIsTreatedAsFollowUp() = runTest {
        val incoming = incomingCompleteMessage.copy(
            content = completeAssetContent.copy(value = completeAssetContent.value.copy(name = ""))
        )
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledSome(listOf("txt")))
            .withValidation(false)
            .withLookup(Either.Right(StoredIncomingAssetMessage.RegularAsset(storedPreviewMessage)))

        arrangement.handler.handle(incoming)

        assertEquals(listOf("fileSharing", "validate", "lookup", "persist"), arrangement.calls)
        assertEquals(1, arrangement.persistedMessages.size)
        assertIs<MessageContent.Asset>(arrangement.persistedMessages.single().content)
    }

    @Test
    fun givenEnabledSomeRejectedNullNameWithIncompleteData_whenHandling_thenItIsRestrictedWithoutLookup() = runTest {
        val incoming = incomingGenericPreviewMessage.copy(
            content = previewAssetContent.copy(value = previewAssetContent.value.copy(name = null))
        )
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledSome(listOf("txt")))
            .withValidation(false)

        arrangement.handler.handle(incoming)

        assertEquals(listOf("fileSharing", "validate", "persist"), arrangement.calls)
        assertEquals(emptyList(), arrangement.lookupArguments)
        assertIs<MessageContent.RestrictedAsset>(arrangement.persistedMessages.single().content)
    }

    @Test
    fun givenAnyLookupLeft_whenHandling_thenItUsesTheExistingMissingMessageFlow() = runTest {
        val expectedFailure = StorageFailure.Generic(IllegalStateException("database unavailable"))
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledAll)
            .withLookup(Either.Left(expectedFailure))

        arrangement.handler.handle(incomingCompleteMessage)

        assertEquals(listOf("fileSharing", "lookup", "persist"), arrangement.calls)
        assertEquals(
            listOf<Message.Standalone>(incomingCompleteMessage.copy(visibility = Message.Visibility.VISIBLE)),
            arrangement.persistedMessages,
        )
    }

    @Test
    fun givenLookupException_whenHandling_thenTheSameExceptionEscapesAndNothingIsPersisted() = runTest {
        val expected = IllegalStateException("lookup failed")
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledAll)
            .apply { lookupResult = { throw expected } }

        val actual = assertFailsWith<IllegalStateException> { arrangement.handler.handle(incomingCompleteMessage) }

        assertSame(expected, actual)
        assertEquals(listOf("fileSharing", "lookup"), arrangement.calls)
        assertEquals(emptyList(), arrangement.persistedMessages)
    }

    @Test
    fun givenLookupCancellation_whenHandling_thenTheSameCancellationEscapesAndNothingIsPersisted() = runTest {
        val expected = CancellationException("lookup cancelled")
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledAll)
            .apply { lookupResult = { throw expected } }

        val actual = assertFailsWith<CancellationException> { arrangement.handler.handle(incomingCompleteMessage) }

        assertSame(expected, actual)
        assertEquals(listOf("fileSharing", "lookup"), arrangement.calls)
        assertEquals(emptyList(), arrangement.persistedMessages)
    }

    @Test
    fun givenStoredAssetFromDifferentSender_whenHandlingFollowUp_thenNoUpdateIsPersisted() = runTest {
        val stored = storedPreviewMessage.copy(senderUserId = UserId("different-sender", "example.com"))
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledAll)
            .withLookup(Either.Right(StoredIncomingAssetMessage.RegularAsset(stored)))

        arrangement.handler.handle(incomingCompleteMessage)

        assertEquals(listOf("fileSharing", "lookup"), arrangement.calls)
        assertEquals(emptyList(), arrangement.persistedMessages)
    }

    @Test
    fun givenStoredUnsupportedRegularMessageFromMatchingSender_whenHandlingFollowUp_thenNoUpdateIsPersisted() = runTest {
        val stored = StoredIncomingAssetMessage.UnsupportedRegular(
            senderUserId = incomingSenderId,
            messageId = "stored-id",
            conversationId = storedConversationId,
            contentType = "Unknown",
        )
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledAll)
            .withLookup(Either.Right(stored))

        arrangement.handler.handle(incomingCompleteMessage)

        assertEquals(listOf("fileSharing", "lookup"), arrangement.calls)
        assertEquals(emptyList(), arrangement.persistedMessages)
    }

    @Test
    fun givenStoredRestrictedAssetFromMatchingSender_whenHandlingFollowUp_thenNoUpdateIsPersisted() = runTest {
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledAll)
            .withLookup(Either.Right(StoredIncomingAssetMessage.RestrictedAsset(incomingSenderId)))

        arrangement.handler.handle(incomingCompleteMessage)

        assertEquals(listOf("fileSharing", "lookup"), arrangement.calls)
        assertEquals(emptyList(), arrangement.persistedMessages)
    }

    @Test
    fun givenStoredSystemMessageFromMatchingSender_whenHandlingFollowUp_thenNoUpdateIsPersisted() = runTest {
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledAll)
            .withLookup(Either.Right(StoredIncomingAssetMessage.System(incomingSenderId)))

        arrangement.handler.handle(incomingCompleteMessage)

        assertEquals(listOf("fileSharing", "lookup"), arrangement.calls)
        assertEquals(emptyList(), arrangement.persistedMessages)
    }

    @Test
    fun givenMatchingStoredAsset_whenHandlingFollowUp_thenOnlyRemoteDataAndVisibilityChange() = runTest {
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.EnabledAll)
            .withLookup(Either.Right(StoredIncomingAssetMessage.RegularAsset(storedPreviewMessage)))

        arrangement.handler.handle(incomingCompleteMessage)

        val storedContent = assertIs<MessageContent.Asset>(storedPreviewMessage.content)
        val expected = storedPreviewMessage.copy(
            content = storedContent.copy(
                value = storedContent.value.copy(remoteData = completeAssetContent.value.remoteData)
            ),
            visibility = Message.Visibility.VISIBLE,
        )
        assertEquals(listOf<Message.Standalone>(expected), arrangement.persistedMessages)
        assertEquals(listOf("fileSharing", "lookup", "persist"), arrangement.calls)
    }

    @Test
    fun givenMatchingStoredAssetAndInvalidFollowUpRemoteData_whenHandling_thenStoredFieldsRemainAndVisibilityBecomesHidden() =
        runTest {
            val invalidRemoteData = completeAssetContent.value.remoteData.copy(otrKey = byteArrayOf())
            val incoming = incomingCompleteMessage.copy(
                content = completeAssetContent.copy(value = completeAssetContent.value.copy(remoteData = invalidRemoteData))
            )
            val arrangement = Arrangement()
                .withFileSharing(FileSharingStatus.Value.EnabledAll)
                .withLookup(Either.Right(StoredIncomingAssetMessage.RegularAsset(storedPreviewMessage)))

            arrangement.handler.handle(incoming)

            val storedContent = assertIs<MessageContent.Asset>(storedPreviewMessage.content)
            assertEquals(
                listOf<Message.Standalone>(
                    storedPreviewMessage.copy(
                        content = storedContent.copy(value = storedContent.value.copy(remoteData = invalidRemoteData)),
                        visibility = Message.Visibility.HIDDEN,
                    )
                ),
                arrangement.persistedMessages,
            )
        }

    @Test
    fun givenPersistReturnsLeft_whenHandling_thenTheFailureIsIgnored() = runTest {
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.Disabled)
            .apply { persistResult = { Either.Left(StorageFailure.DataNotFound) } }

        arrangement.handler.handle(incomingCompleteMessage)

        assertEquals(listOf("fileSharing", "persist"), arrangement.calls)
        assertEquals(1, arrangement.persistedMessages.size)
    }

    @Test
    fun givenPersistException_whenHandling_thenTheSameExceptionEscapes() = runTest {
        val expected = IllegalStateException("persist failed")
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.Disabled)
            .apply { persistResult = { throw expected } }

        val actual = assertFailsWith<IllegalStateException> { arrangement.handler.handle(incomingCompleteMessage) }

        assertSame(expected, actual)
        assertEquals(listOf("fileSharing", "persist"), arrangement.calls)
    }

    @Test
    fun givenPersistCancellation_whenHandling_thenTheSameCancellationEscapes() = runTest {
        val expected = CancellationException("persist cancelled")
        val arrangement = Arrangement()
            .withFileSharing(FileSharingStatus.Value.Disabled)
            .apply { persistResult = { throw expected } }

        val actual = assertFailsWith<CancellationException> { arrangement.handler.handle(incomingCompleteMessage) }

        assertSame(expected, actual)
        assertEquals(listOf("fileSharing", "persist"), arrangement.calls)
    }

    private class Arrangement {
        val calls = mutableListOf<String>()
        val validationArguments = mutableListOf<ValidationArguments>()
        val lookupArguments = mutableListOf<Pair<ConversationId, String>>()
        val persistedMessages = mutableListOf<Message.Standalone>()

        var fileSharingResult: suspend () -> Either<StorageFailure, FileSharingStatus> = {
            error("Unexpected file-sharing lookup")
        }
        var validationResult: () -> Boolean = { error("Unexpected validation") }
        var lookupResult: suspend () -> Either<StorageFailure, StoredIncomingAssetMessage> = {
            error("Unexpected message lookup")
        }
        var persistResult: suspend () -> Either<CoreFailure, Unit> = { Either.Right(Unit) }

        private val fileSharingStatusProvider = FileSharingStatusProvider {
            calls += "fileSharing"
            fileSharingResult()
        }
        private val incomingAssetMessageLookup = object : IncomingAssetMessageLookup {
            override suspend fun getMessageById(
                conversationId: ConversationId,
                messageId: String,
            ): Either<StorageFailure, StoredIncomingAssetMessage> {
                calls += "lookup"
                lookupArguments += conversationId to messageId
                return lookupResult()
            }
        }
        private val validateAssetFileTypeUseCase = object : ValidateAssetFileTypeUseCase {
            override fun invoke(fileName: String?, mimeType: String, allowedExtension: List<String>): Boolean {
                calls += "validate"
                validationArguments += ValidationArguments(fileName, mimeType, allowedExtension)
                return validationResult()
            }
        }
        private val persistMessage = object : PersistMessageUseCase {
            override suspend fun invoke(message: Message.Standalone): Either<CoreFailure, Unit> {
                calls += "persist"
                persistedMessages += message
                return persistResult()
            }
        }

        val handler: AssetMessageHandler = AssetMessageHandlerImpl(
            incomingAssetMessageLookup = incomingAssetMessageLookup,
            persistMessage = persistMessage,
            fileSharingStatusProvider = fileSharingStatusProvider,
            validateAssetMimeTypeUseCase = validateAssetFileTypeUseCase,
        )

        fun withFileSharing(value: FileSharingStatus.Value) = apply {
            fileSharingResult = { Either.Right(FileSharingStatus(value, isStatusChanged = false)) }
        }

        fun withValidation(value: Boolean) = apply { validationResult = { value } }

        fun withLookup(value: Either<StorageFailure, StoredIncomingAssetMessage>) = apply {
            lookupResult = { value }
        }
    }

    private data class ValidationArguments(
        val fileName: String?,
        val mimeType: String,
        val allowedExtension: List<String>,
    )

    private companion object {
        val incomingSenderId = UserId("sender", "example.com")
        val incomingConversationId = ConversationId("incoming-conversation", "example.com")
        val storedConversationId = ConversationId("stored-conversation", "example.com")
        val completeRemoteData = AssetContent.RemoteData(
            otrKey = byteArrayOf(1, 2),
            sha256 = byteArrayOf(3, 4),
            assetId = "asset-id",
            assetDomain = "assets.example.com",
            assetToken = "asset-token",
            encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM,
        )
        val previewRemoteData = AssetContent.RemoteData(
            otrKey = byteArrayOf(),
            sha256 = byteArrayOf(),
            assetId = "",
            assetDomain = "",
            assetToken = "",
            encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM,
        )
        val completeAssetContent = MessageContent.Asset(
            AssetContent(
                sizeInBytes = 100L,
                name = "archive.zip",
                mimeType = "application/zip",
                metadata = null,
                remoteData = completeRemoteData,
            )
        )
        val previewAssetContent = MessageContent.Asset(
            AssetContent(
                sizeInBytes = 100L,
                name = "archive.zip",
                mimeType = "application/zip",
                metadata = null,
                remoteData = previewRemoteData,
            )
        )
        val incomingCompleteMessage = Message.Regular(
            id = "incoming-message",
            content = completeAssetContent,
            conversationId = incomingConversationId,
            date = Instant.parse("2026-08-19T08:00:00Z"),
            senderUserId = incomingSenderId,
            senderClientId = ClientId("incoming-client"),
            status = Message.Status.Sent,
            editStatus = Message.EditStatus.NotEdited,
            isSelfMessage = false,
        )
        val incomingGenericPreviewMessage = incomingCompleteMessage.copy(content = previewAssetContent)
        val storedAssetContent = MessageContent.Asset(
            AssetContent(
                sizeInBytes = 777L,
                name = "stored-preview.mp4",
                mimeType = "video/mp4",
                metadata = AssetContent.AssetMetadata.Video(width = 640, height = 480, durationMs = 9_000L),
                remoteData = previewRemoteData.copy(assetDomain = "stored-assets.example.com"),
            )
        )
        val storedPreviewMessage = Message.Regular(
            id = "stored-message",
            content = storedAssetContent,
            conversationId = storedConversationId,
            date = Instant.parse("2026-08-18T07:00:00Z"),
            senderUserId = incomingSenderId,
            status = Message.Status.Read(12L),
            visibility = Message.Visibility.DELETED,
            senderUserName = "Stored Sender",
            isSelfMessage = true,
            senderClientId = ClientId("stored-client"),
            editStatus = Message.EditStatus.Edited(Instant.parse("2026-08-18T07:01:00Z")),
            expirationData = Message.ExpirationData(
                expireAfter = 30.seconds,
                selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.Started(
                    Instant.parse("2026-08-18T07:02:00Z")
                ),
            ),
            reactions = Message.Reactions(mapOf("👍" to Message.ReactionData(count = 2, isSelf = true))),
            expectsReadConfirmation = true,
            deliveryStatus = DeliveryStatus.PartialDelivery(
                recipientsFailedWithNoClients = listOf(UserId("no-client", "example.com")),
                recipientsFailedDelivery = listOf(UserId("failed", "example.com")),
            ),
        )
    }
}
