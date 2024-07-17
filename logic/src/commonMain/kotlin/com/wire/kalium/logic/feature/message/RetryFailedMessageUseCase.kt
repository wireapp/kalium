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

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.getType
import com.wire.kalium.logic.feature.asset.GetAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Suppress("LongParameterList")
class RetryFailedMessageUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val assetRepository: AssetRepository,
    private val persistMessage: PersistMessageUseCase,
    private val scope: CoroutineScope,
    private val dispatcher: KaliumDispatcher,
    private val messageSender: MessageSender,
    private val updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
    private val getAssetMessageTransferStatusUseCase: GetAssetMessageTransferStatusUseCase,
    private val messageSendFailureHandler: MessageSendFailureHandler,
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
     * @return [Either.Left] in case the message could not be found or has invalid status, [Either.Right] otherwise. Note that this doesn't
     * imply that send will succeed, it just confirms that resending is the valid action for this message, and it has been started.
     */
    suspend operator fun invoke(
        messageId: String,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> =
        messageRepository.getMessageById(conversationId, messageId)
            .flatMap { message ->
                when (message.status) {
                    Message.Status.Failed, Message.Status.FailedRemotely -> {
                        messageRepository.updateMessageStatus(
                            messageStatus = MessageEntity.Status.PENDING,
                            conversationId = message.conversationId,
                            messageUuid = message.id
                        )
                        scope.launch(dispatcher.io) {
                            val content = message.content
                            when {
                                message is Message.Regular && content is MessageContent.Asset ->
                                    retrySendingAssetMessage(message, content.value)

                                message is Message.Regular && message.editStatus is Message.EditStatus.Edited ->
                                    retrySendingEditMessage(message)

                                message is Message.Sendable -> retrySendingMessage(message)

                                else -> handleError("Message of type ${message::class.simpleName} cannot be retried")
                            }
                        }
                        Either.Right(Unit)
                    }

                    else -> handleError("Message with status ${message.status} cannot be retried")
                }
            }
            .map { /* returns Unit */ }

    private suspend fun retrySendingMessage(message: Message.Sendable): Either<CoreFailure, Unit> =
        messageSender.sendMessage(message)
            .onFailure {
                val type = message.content.getType()
                kaliumLogger.e("Failed to retry sending message of type $type. Failure = $it")
                messageSendFailureHandler.handleFailureAndUpdateMessageStatus(it, message.conversationId, message.id, type)
            }

    private suspend fun retrySendingEditMessage(message: Message.Regular): Either<CoreFailure, Unit> =
        when (val content = message.content) {
            is MessageContent.Text -> {
                val editContent = MessageContent.TextEdited(
                    editMessageId = message.id,
                    newContent = content.value,
                    newMentions = content.mentions
                )
                // Create new unique message ID
                val generatedMessageUuid = uuid4().toString()
                val editMessage = Message.Signaling(
                    id = generatedMessageUuid,
                    content = editContent,
                    conversationId = message.conversationId,
                    date = Clock.System.now(),
                    senderUserId = message.senderUserId,
                    senderClientId = message.senderClientId,
                    status = Message.Status.Pending,
                    isSelfMessage = true,
                    expirationData = null
                )
                retrySendingMessage(editMessage)
            }

            else -> handleError("Message edit with content of type ${content::class.simpleName} cannot be retried")
        }

    private suspend fun retrySendingAssetMessage(
        message: Message.Regular,
        content: AssetContent,
    ): Either<CoreFailure, Unit> {
        val assetTransferStatus = getAssetMessageTransferStatusUseCase(message.conversationId, message.id)

        return when (assetTransferStatus) {
            AssetTransferStatus.FAILED_UPLOAD, AssetTransferStatus.NOT_DOWNLOADED -> {
                updateAssetMessageTransferStatus(AssetTransferStatus.UPLOAD_IN_PROGRESS, message.conversationId, message.id)
                retryUploadingAsset(content)
                    .flatMap { uploadedAssetContent ->
                        message.copy(content = MessageContent.Asset(value = uploadedAssetContent)).let { updatedMessage ->
                            persistMessage(updatedMessage)
                                .map { updatedMessage } // we need to persist new asset remoteData and status
                        }
                    }
                    .onFailure {
                        kaliumLogger.e("Failed to retry sending asset message. Failure = $it")
                        updateAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD, message.conversationId, message.id)
                        val type = message.content.getType()
                        messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                            failure = it,
                            conversationId = message.conversationId,
                            messageId = message.id,
                            messageType = type,
                            scheduleResendIfNoNetwork = false
                        )
                    }
            }

            AssetTransferStatus.UPLOADED -> Either.Right(message)

            else -> handleError("Asset message with transfer status $assetTransferStatus cannot be retried")
        }
            .onSuccess { retrySendingMessage(it) }
            .map { /* returns Unit */ }
    }

    private suspend fun retryUploadingAsset(assetContent: AssetContent): Either<CoreFailure, AssetContent> =
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
                .flatMap { assetDataPath ->
                    assetRepository.uploadAndPersistPrivateAsset(
                        mimeType = mimeType,
                        assetDataPath = assetDataPath,
                        otrKey = AES256Key(remoteData.otrKey),
                        extension = name?.fileExtension() ?: ""
                    )
                }
                .map { (uploadedAssetId, sha256key) ->
                    assetContent.copy(
                        remoteData = assetContent.remoteData.copy(
                            sha256 = sha256key.data,
                            assetId = uploadedAssetId.key,
                            assetDomain = uploadedAssetId.domain,
                            assetToken = uploadedAssetId.assetToken
                        ),
                    )
                }
        }

    private fun handleError(message: String): Either.Left<CoreFailure> =
        Either.Left(CoreFailure.Unknown(IllegalStateException(message)))
            .also { kaliumLogger.e(message) }
}
