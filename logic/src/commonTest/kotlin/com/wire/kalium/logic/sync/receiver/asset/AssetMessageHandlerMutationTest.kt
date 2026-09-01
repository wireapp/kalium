/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.ValidateAssetFileTypeUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.ValidateAssetFileTypeUseCaseArrangementImpl
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@Suppress("TooManyFunctions")
class AssetMessageHandlerMutationTest {

    @Test
    fun givenNonAssetMessage_whenHandling_thenMessageIsIgnored() = runTest {
        val (arrangement, handler) = arrange {}

        handler.handle(TestMessage.TEXT_MESSAGE)

        verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.isFileSharingEnabled() }
        verifySuspend(VerifyMode.not) { arrangement.persistMessageUseCase.invoke(any()) }
    }

    @Test
    fun givenFileSharingDisabled_whenHandling_thenRestrictedAssetIsPersisted() = runTest {
        val message = assetMessage(completeAsset())
        val (arrangement, handler) = arrange {
            withFileSharing(FileSharingStatus.Value.Disabled)
            withPersistingMessage(Either.Right(Unit))
        }

        handler.handle(message)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(
                matches {
                    it is Message.Regular && it.content == MessageContent.RestrictedAsset(
                        mimeType = completeAsset().mimeType,
                        sizeInBytes = completeAsset().sizeInBytes,
                        name = completeAsset().name.orEmpty(),
                    )
                }
            )
        }
        verifySuspend(VerifyMode.not) { arrangement.messageRepository.getMessageById(any(), any()) }
    }

    @Test
    fun givenFileSharingEnabledAndNewCompleteAsset_whenHandling_thenVisibleAssetIsPersisted() = runTest {
        val message = assetMessage(completeAsset())
        val (arrangement, handler) = arrange {
            withFileSharing(FileSharingStatus.Value.EnabledAll)
            withMessageNotFound()
            withPersistingMessage(Either.Right(Unit))
        }

        handler.handle(message)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(matches { it is Message.Regular && it.visibility == Message.Visibility.VISIBLE })
        }
    }

    @Test
    fun givenFileSharingEnabledAndIncompletePreview_whenHandling_thenHiddenAssetIsPersisted() = runTest {
        val message = assetMessage(incompleteAsset())
        val (arrangement, handler) = arrange {
            withFileSharing(FileSharingStatus.Value.EnabledAll)
            withMessageNotFound()
            withPersistingMessage(Either.Right(Unit))
        }

        handler.handle(message)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(matches { it is Message.Regular && it.visibility == Message.Visibility.HIDDEN })
        }
    }

    @Test
    fun givenAllowedFileType_whenHandling_thenAssetProcessingContinues() = runTest {
        val message = assetMessage(completeAsset())
        val allowedTypes = listOf("pdf")
        val (arrangement, handler) = arrange {
            withFileSharing(FileSharingStatus.Value.EnabledSome(allowedTypes))
            withValidateAssetFileTypeReturning(true)
            withMessageNotFound()
            withPersistingMessage(Either.Right(Unit))
        }

        handler.handle(message)

        verify(VerifyMode.exactly(1)) {
            arrangement.validateAssetFileTypeUseCase(
                eq(completeAsset().name),
                eq(completeAsset().mimeType),
                eq(allowedTypes),
            )
        }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.persistMessageUseCase.invoke(message) }
    }

    @Test
    fun givenDisallowedNamelessCompleteAssetWithoutPreview_whenHandling_thenRestrictedAssetIsPersisted() = runTest {
        val content = completeAsset().copy(name = null)
        val message = assetMessage(content)
        val (arrangement, handler) = arrange {
            withFileSharing(FileSharingStatus.Value.EnabledSome(listOf("pdf")))
            withValidateAssetFileTypeReturning(false)
            withMessageNotFound()
            withPersistingMessage(Either.Right(Unit))
        }

        handler.handle(message)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(matches { it.content is MessageContent.RestrictedAsset })
        }
    }

    @Test
    fun givenDisallowedNamedAsset_whenHandling_thenRestrictedAssetIsPersistedImmediately() = runTest {
        val content = incompleteAsset().copy(name = "blocked.exe")
        val message = assetMessage(content)
        val (arrangement, handler) = arrange {
            withFileSharing(FileSharingStatus.Value.EnabledSome(listOf("pdf")))
            withValidateAssetFileTypeReturning(false)
            withPersistingMessage(Either.Right(Unit))
        }

        handler.handle(message)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(
                matches {
                    it.content == MessageContent.RestrictedAsset(
                        mimeType = content.mimeType,
                        sizeInBytes = content.sizeInBytes,
                        name = content.name.orEmpty(),
                    )
                }
            )
        }
        verifySuspend(VerifyMode.not) { arrangement.messageRepository.getMessageById(any(), any()) }
    }

    @Test
    fun givenDisallowedNamelessIncompleteAsset_whenHandling_thenRestrictedAssetIsPersistedImmediately() = runTest {
        val content = incompleteAsset().copy(name = null)
        val message = assetMessage(content)
        val (arrangement, handler) = arrange {
            withFileSharing(FileSharingStatus.Value.EnabledSome(listOf("pdf")))
            withValidateAssetFileTypeReturning(false)
            withPersistingMessage(Either.Right(Unit))
        }

        handler.handle(message)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(matches { it.content is MessageContent.RestrictedAsset })
        }
        verifySuspend(VerifyMode.not) { arrangement.messageRepository.getMessageById(any(), any()) }
    }

    @Test
    fun givenFollowUpAssetFromSameSender_whenHandling_thenDecryptionKeysAreUpdated() = runTest {
        val previewContent = incompleteAsset()
        val persistedPreview = assetMessage(previewContent)
        val followUpContent = previewContent.copy(remoteData = validRemoteData("follow-up-asset"))
        val followUpMessage = assetMessage(followUpContent)
        val (arrangement, handler) = arrange {
            withFileSharing(FileSharingStatus.Value.EnabledAll)
            withPersistedMessage(persistedPreview)
            withPersistingMessage(Either.Right(Unit))
        }

        handler.handle(followUpMessage)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(
                matches {
                    val asset = (it.content as? MessageContent.Asset)?.value
                    it.visibility == Message.Visibility.VISIBLE && asset?.remoteData == followUpContent.remoteData
                }
            )
        }
    }

    @Test
    fun givenFollowUpAssetFromDifferentSender_whenHandling_thenExistingMessageIsNotChanged() = runTest {
        val persistedPreview = assetMessage(incompleteAsset())
        val followUpMessage = assetMessage(completeAsset()).copy(senderUserId = TestUser.OTHER_USER_ID)
        val (arrangement, handler) = arrange {
            withFileSharing(FileSharingStatus.Value.EnabledAll)
            withPersistedMessage(persistedPreview)
        }

        handler.handle(followUpMessage)

        verifySuspend(VerifyMode.not) { arrangement.persistMessageUseCase.invoke(any()) }
    }

    @Test
    fun givenPersistedMessageIsNotAnAssetPreview_whenHandlingFollowUp_thenMessageIsNotChanged() = runTest {
        val nonAssetContents = listOf(
            MessageContent.RestrictedAsset("application/pdf", 100, "restricted.pdf"),
            MessageContent.FailedDecryption(isDecryptionResolved = false, senderUserId = TestUser.USER_ID),
            MessageContent.Knock(hotKnock = false),
            MessageContent.Location(latitude = 1F, longitude = 2F),
            MessageContent.Composite(textContent = null, buttonList = emptyList()),
            MessageContent.Text("text"),
            MessageContent.Multipart(value = "multipart"),
            MessageContent.Unknown(),
        )

        nonAssetContents.forEach { persistedContent ->
            val persistedMessage = assetMessage(incompleteAsset()).copy(content = persistedContent)
            val (arrangement, handler) = arrange {
                withFileSharing(FileSharingStatus.Value.EnabledAll)
                withPersistedMessage(persistedMessage)
            }

            handler.handle(assetMessage(completeAsset()))

            verifySuspend(VerifyMode.not) { arrangement.persistMessageUseCase.invoke(any()) }
        }
    }

    @Test
    fun givenFileSharingLookupFails_whenHandling_thenAssetIsNotPersisted() = runTest {
        val (arrangement, handler) = arrange {
            withFileSharingFailure()
        }

        handler.handle(assetMessage(completeAsset()))

        verifySuspend(VerifyMode.not) { arrangement.persistMessageUseCase.invoke(any()) }
        verifySuspend(VerifyMode.not) { arrangement.messageRepository.getMessageById(any(), any()) }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit,
    ) : MessageRepositoryArrangement by MessageRepositoryArrangementImpl(),
        UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl(),
        PersistMessageUseCaseArrangement by PersistMessageUseCaseArrangementImpl(),
        ValidateAssetFileTypeUseCaseArrangement by ValidateAssetFileTypeUseCaseArrangementImpl() {

        suspend fun arrange() = run {
            block()
            this@Arrangement to AssetMessageHandlerImpl(
                messageRepository = messageRepository,
                persistMessage = persistMessageUseCase,
                userConfigRepository = userConfigRepository,
                validateAssetMimeTypeUseCase = validateAssetFileTypeUseCase,
            )
        }

        suspend fun withFileSharing(state: FileSharingStatus.Value) {
            withFileSharingEnabledReturning(Either.Right(FileSharingStatus(state, isStatusChanged = false)))
        }

        suspend fun withFileSharingFailure() {
            withFileSharingEnabledReturning(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun withMessageNotFound() {
            withGetMessageById(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun withPersistedMessage(message: Message) {
            withGetMessageById(Either.Right(message))
        }
    }

    private companion object {
        fun assetMessage(assetContent: AssetContent) = TestMessage.TEXT_MESSAGE.copy(
            content = MessageContent.Asset(assetContent),
            senderUserId = TestUser.USER_ID,
        )

        fun completeAsset() = AssetContent(
            sizeInBytes = 100,
            name = "document.pdf",
            mimeType = "application/pdf",
            remoteData = validRemoteData("asset-id"),
        )

        fun incompleteAsset() = AssetContent(
            sizeInBytes = 100,
            name = "document.pdf",
            mimeType = "application/pdf",
            remoteData = AssetContent.RemoteData(
                otrKey = byteArrayOf(),
                sha256 = byteArrayOf(),
                assetId = "",
                assetToken = null,
                assetDomain = null,
                encryptionAlgorithm = null,
            ),
        )

        fun validRemoteData(assetId: String) = AssetContent.RemoteData(
            otrKey = byteArrayOf(1),
            sha256 = byteArrayOf(2),
            assetId = assetId,
            assetToken = "token",
            assetDomain = "wire.com",
            encryptionAlgorithm = null,
        )
    }
}
