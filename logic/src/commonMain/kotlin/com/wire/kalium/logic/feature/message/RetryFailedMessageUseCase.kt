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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.feature.asset.upload.uploadPreviewIfNeeded
import com.wire.kalium.logic.feature.asset.upload.deleteTemporaryPreview
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCase
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.isSuccessfullySent
import com.wire.kalium.logic.data.message.MessageAttachment
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.getType
import com.wire.kalium.logic.feature.asset.GetAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// todo(interface). extract interface for use case
@Suppress("LongParameterList")
public class RetryFailedMessageUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val assetRepository: AssetRepository,
    private val conversationRepository: ConversationRepository,
    private val attachmentsRepository: MessageAttachmentDraftRepository,
    private val persistMessage: PersistMessageUseCase,
    private val publishAttachments: PublishAttachmentsUseCase,
    private val scope: CoroutineScope,
    private val dispatcher: KaliumDispatcher,
    private val messageSender: MessageSender,
    private val updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
    private val getAssetMessageTransferStatusUseCase: GetAssetMessageTransferStatusUseCase,
) {

    /**
     * Function that enables resending of failed message to a given conversation with the strategy of fire & forget.
     * This message must have a status of [Message.Status.Failed] or [Message.Status.FailedRemotely].
     *
     * If it's an asset message, the asset may or may not be already uploaded. The asset will be uploaded if needed.
     *
     * The resending and possible reuploading of assets are scheduled but not awaited, so returning [Either.Right] doesn't mean that
     * the message has been sent successfully.
     *
     * @param messageId the id of the failed message to be resent
     * @param conversationId the id of the conversation where the failed message wants to be resent
     * @return [MessageOperationResult] with a [MessageOperationResult.Failure]
     * in case the message could not be found, has invalid status, or has unsupported content, [MessageOperationResult.Success] otherwise.
     * Success confirms that resending is valid and has been started; it does not imply that the send will succeed.
     */
    public suspend operator fun invoke(messageId: String, conversationId: ConversationId): MessageOperationResult {
        var owned: Message? = null
        var scheduled = false
        try {
            return prepareRetry(messageId, conversationId) { owned = it }
                .onSuccess { message ->
                    scope.launch(dispatcher.io, start = CoroutineStart.UNDISPATCHED) { executeRetry(message) }
                    scheduled = true
                }
                .fold({ MessageOperationResult.Failure(it) }, { MessageOperationResult.Success })
        } finally {
            if (!scheduled) withContext(NonCancellable + dispatcher.io) { owned?.let { markFailed(it) } }
        }
    }

    /**
     * Retries the existing persisted message and returns its actual upload/send result.
     * Work belongs to the calling coroutine, rather than the session's fire-and-forget scope.
     * Cancellation waits for upload/send unwinding and marks unfinished delivery failed before leaving;
     * confirmed successful sends remain sent. No automatic network resend is scheduled.
     * A send skipped because the message is no longer eligible cancels the retry without failure-state cleanup.
     * The message ID and uploaded remote data are preserved.
     */
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun invokeAndAwait(messageId: String, conversationId: ConversationId): MessageOperationResult {
        var owned: Message? = null
        var enteredRetry = false
        try {
            return withContext(dispatcher.io) {
                prepareRetry(messageId, conversationId) { owned = it }.flatMap { message ->
                    enteredRetry = true
                    executeRetry(message, awaitDelivery = true)
                }.fold({ MessageOperationResult.Failure(it) }, { MessageOperationResult.Success })
            }
        } finally {
            // Ownership can commit before cancellation prevents entry into executeRetry.
            if (!enteredRetry) withContext(NonCancellable + dispatcher.io) { owned?.let { markFailed(it) } }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun executeRetry(message: Message, awaitDelivery: Boolean = false): Either<CoreFailure, Unit> {
        var skipped = false
        var success = false
        var failure: CoreFailure? = null
        try {
            return retry(message) { sendable ->
                if (!awaitDelivery) messageSender.sendMessage(sendable) else {
                    messageSender.sendMessageWithOutcome(sendable).map { sent ->
                        if (!sent) {
                            skipped = true
                            throw CancellationException("Retry message is no longer eligible for sending")
                        }
                    }
                }
            }.onSuccess { success = true }.onFailure { failure = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            return CoreFailure.Unknown(exception).also { failure = it }.left()
        } finally {
            // The sole terminal write, after all transfer/send work has unwound.
            if (!success && !skipped) withContext(NonCancellable) { markFailed(message, failure) }
        }
    }

    private suspend fun prepareRetry(
        messageId: String,
        conversationId: ConversationId,
        onValidated: (Message) -> Unit = {},
    ): Either<CoreFailure, Message> = messageRepository.getMessageById(conversationId, messageId).flatMap { message ->
        if (message.status != Message.Status.Failed && message.status != Message.Status.FailedRemotely) {
            return@flatMap handleError("Message with status ${message.status} cannot be retried")
        }
        when (message.content) {
            is MessageContent.Unknown,
            is MessageContent.CompositeEdited,
            is MessageContent.FailedDecryption,
            is MessageContent.RestrictedAsset,
            MessageContent.Ignored -> return@flatMap handleError("Message with ${message.content.getType()} content cannot be retried")
            else -> Unit
        }
        // Complete the short claim and record ownership before cancellation can discard its result.
        withContext(NonCancellable) {
            messageRepository.claimFailedMessageForRetry(message.conversationId, message.id).flatMap { claimed ->
                if (claimed) {
                    onValidated(message)
                    message.right()
                } else {
                    handleError("Message is no longer available for retry")
                }
            }
        }.flatMap {
            messageRepository.getMessageById(conversationId, messageId).map { current ->
                val status = if (current.status.isSuccessfullySent()) current.status else Message.Status.Pending
                when (current) {
                    is Message.Regular -> current.copy(status = status)
                    is Message.Signaling -> current.copy(status = status)
                    is Message.System -> current.copy(status = status)
                }
            }
        }
    }

    private suspend fun retry(
        message: Message,
        send: suspend (Message.Sendable) -> Either<CoreFailure, Unit>,
    ): Either<CoreFailure, Unit> {
        if (message.status.isSuccessfullySent()) return Unit.right()
        val content = message.content
        return when {
            message is Message.Regular && content is MessageContent.Asset -> retrySendingAssetMessage(message, content.value, send)
            message is Message.Regular && message.editStatus is Message.EditStatus.Edited -> retrySendingEditMessage(message, send)
            message is Message.Regular && content is MessageContent.Multipart -> retrySendingMultipartMessage(message, send)
            message is Message.Sendable -> send(message)
            else -> handleError("Message of type ${message::class.simpleName} cannot be retried")
        }
    }

    private suspend fun markFailed(message: Message, failure: CoreFailure? = null) {
        // Crypto transactions may finish delivery under NonCancellable and lose their result on return.
        // Never turn an acknowledged persisted send into a retryable failure during cleanup.
        val persisted = messageRepository.getMessageById(message.conversationId, message.id).getOrNull()
        if (persisted?.status?.isSuccessfullySent() == true) return
        if (message.content is MessageContent.Asset &&
            getAssetMessageTransferStatusUseCase(message.conversationId, message.id) != AssetTransferStatus.UPLOADED
        ) {
            // Remote data is persisted before UPLOADED; cancellation can interrupt between those writes.
            val content = persisted?.content as? MessageContent.Asset
            val uploaded = content?.value?.remoteData?.sha256?.isNotEmpty() == true
            updateAssetMessageTransferStatus(
                if (uploaded) AssetTransferStatus.UPLOADED else AssetTransferStatus.FAILED_UPLOAD,
                message.conversationId,
                message.id
            )
        }
        val status = if (failure is NetworkFailure.FederatedBackendFailure) {
            MessageEntity.Status.FAILED_REMOTELY
        } else {
            MessageEntity.Status.FAILED
        }
        messageRepository.updateMessageStatusIfNotAcknowledged(status, message.conversationId, message.id)
    }

    private suspend fun retrySendingEditMessage(
        message: Message.Regular,
        send: suspend (Message.Sendable) -> Either<CoreFailure, Unit>,
    ): Either<CoreFailure, Unit> =
        when (val content = message.content) {
            is MessageContent.Text ->
                send(EditMessageBuilder.buildTextEditSignaling(message, content))

            else -> handleError("Message edit with content of type ${content::class.simpleName} cannot be retried")
        }

    private suspend fun retrySendingAssetMessage(
        message: Message.Regular,
        content: AssetContent,
        send: suspend (Message.Sendable) -> Either<CoreFailure, Unit>,
    ): Either<CoreFailure, Unit> {
        val assetTransferStatus = getAssetMessageTransferStatusUseCase(message.conversationId, message.id)

        return when (assetTransferStatus) {
            AssetTransferStatus.FAILED_UPLOAD, AssetTransferStatus.NOT_DOWNLOADED -> {
                updateAssetMessageTransferStatus(AssetTransferStatus.UPLOAD_IN_PROGRESS, message.conversationId, message.id)
                retryUploadingAsset(message.conversationId, content)
                    .flatMap { uploadedAssetContent ->
                        message.copy(content = MessageContent.Asset(value = uploadedAssetContent)).let { updatedMessage ->
                            withContext(NonCancellable) {
                                persistMessage(updatedMessage).onSuccess {
                                    assetRepository.deleteAssetLocally(content.remoteData.assetId)
                                }
                            }.map { updatedMessage } // we need to persist new asset remoteData and status
                        }
                    }
                    .onSuccess { updatedMessage ->
                        updateAssetMessageTransferStatus(AssetTransferStatus.UPLOADED, message.conversationId, message.id)
                        assetRepository.deleteAssetLocally(content.remoteData.assetId)
                        assetRepository.deleteTemporaryPreview(
                            content.preview,
                            (updatedMessage.content as MessageContent.Asset).value.preview
                        )
                    }
            }

            AssetTransferStatus.UPLOADED -> Either.Right(message)

            else -> handleError("Asset message with transfer status $assetTransferStatus cannot be retried")
        }
            .flatMap { send(it) }
    }

    private suspend fun retryUploadingAsset(conversationId: ConversationId, assetContent: AssetContent): Either<CoreFailure, AssetContent> =
        with(assetContent) {
            assetRepository.fetchPrivateDecodedAsset(
                assetId = remoteData.assetId,
                assetDomain = remoteData.assetDomain,
                assetName = name ?: "",
                assetToken = remoteData.assetToken,
                encryptionKey = AES256Key(remoteData.otrKey),
                assetSHA256Key = SHA256Key(remoteData.sha256),
                mimeType = mimeType,
                downloadIfNeeded = false
            )
                .flatMap { (assetDataPath, _) ->
                    assetRepository.uploadAndPersistPrivateAsset(
                        mimeType = mimeType,
                        assetDataPath = assetDataPath,
                        otrKey = AES256Key(remoteData.otrKey),
                        extension = name?.fileExtension() ?: "",
                        conversationId = conversationId.toApi(),
                        filename = name,
                        filetype = mimeType,
                        retainSource = true,
                    )
                }
                .map { (uploadedAssetId, sha256key) ->
                    assetContent.copy(
                        preview = assetRepository.uploadPreviewIfNeeded(preview, conversationId),
                        remoteData = assetContent.remoteData.copy(
                            sha256 = sha256key.data,
                            assetId = uploadedAssetId.key,
                            assetDomain = uploadedAssetId.domain,
                            assetToken = uploadedAssetId.assetToken
                        ),
                    )
                }
        }

    private suspend fun retrySendingMultipartMessage(
        message: Message.Regular,
        send: suspend (Message.Sendable) -> Either<CoreFailure, Unit>,
    ): Either<CoreFailure, Unit> {

        val isCellEnabled = conversationRepository.isCellEnabled(message.conversationId)
            .onFailure {
                return it.left()
            }
            .getOrElse(false)

        return if (isCellEnabled) {
            val attachments: List<MessageAttachment> = attachmentsRepository.getAll(message.conversationId)
                .getOrElse { emptyList() }
                .map {
                    CellAssetContent(
                        id = it.uuid,
                        versionId = it.versionId,
                        mimeType = it.mimeType,
                        assetPath = it.remoteFilePath,
                        assetSize = it.fileSize,
                        localPath = it.localFilePath,
                        previewUrl = null,
                        metadata = it.metadata(),
                        transferStatus = AssetTransferStatus.SAVED_INTERNALLY,
                    )
                }

            publishAttachments(attachments).flatMap { send(message) }
        } else {
            send(message)
        }
    }

    private fun handleError(message: String): Either.Left<CoreFailure> =
        Either.Left(CoreFailure.Unknown(IllegalStateException(message)))
            .also { kaliumLogger.e(message) }
}
