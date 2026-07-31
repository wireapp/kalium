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

package com.wire.kalium.logic.sync

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.isAudioMimeType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.asset.GetAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Handles sending a pending asset message, retrying the upload if the previous attempt failed due to
 * no network connectivity. This is called by pending-message retry when network is restored.
 *
 * Unlike [com.wire.kalium.logic.feature.message.RetryFailedMessageUseCase] which operates on
 * messages with [Message.Status.Failed] status, this use case operates on messages that remain
 * in [Message.Status.Pending] state after an upload failure caused by [com.wire.kalium.common.error.NetworkFailure.NoNetworkConnection].
 */
internal interface SendPendingAssetMessageUseCase {
    suspend operator fun invoke(message: Message.Regular): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class SendPendingAssetMessageUseCaseImpl(
    private val assetRepository: AssetRepository,
    private val persistMessage: PersistMessageUseCase,
    private val updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
    private val getAssetMessageTransferStatus: GetAssetMessageTransferStatusUseCase,
    private val messageSender: MessageSender,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder,
    private val pendingMessagesEnabled: Boolean = true,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : SendPendingAssetMessageUseCase {

    override suspend fun invoke(message: Message.Regular): Either<CoreFailure, Unit> {
        val content = message.content as? MessageContent.Asset
            ?: return Either.Left(CoreFailure.Unknown(IllegalStateException("Not an asset message: ${message.id}")))

        return when (val status = getAssetMessageTransferStatus(message.conversationId, message.id)) {
            AssetTransferStatus.FAILED_UPLOAD -> reuploadAndSend(message, content.value)
            AssetTransferStatus.UPLOADED -> messageSender.sendPendingMessage(message.conversationId, message.id)
            else -> {
                kaliumLogger.i("Skipping pending asset message ${message.id} with transfer status $status")
                Either.Right(Unit)
            }
        }
    }

    private suspend fun reuploadAndSend(message: Message.Regular, content: AssetContent): Either<CoreFailure, Unit> {
        updateAssetMessageTransferStatus(AssetTransferStatus.UPLOAD_IN_PROGRESS, message.conversationId, message.id)
        return uploadAsset(message.conversationId, content)
            .flatMap { uploadedContent ->
                val updatedMessage = message.copy(content = MessageContent.Asset(uploadedContent))
                persistMessage(updatedMessage).map { updatedMessage }
            }
            .onSuccess { updatedMessage ->
                updateAssetMessageTransferStatus(AssetTransferStatus.UPLOADED, message.conversationId, message.id)
                assetRepository.deleteAssetLocally(content.remoteData.assetId)
                messageSender.sendMessage(updatedMessage).onFailure {
                    messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                        failure = it,
                        conversationId = message.conversationId,
                        messageId = message.id,
                        messageType = TYPE,
                        scheduleResendIfNoNetwork = pendingMessagesEnabled
                    )
                }
            }
            .onFailure {
                updateAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD, message.conversationId, message.id)
                messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                    failure = it,
                    conversationId = message.conversationId,
                    messageId = message.id,
                    messageType = TYPE,
                    scheduleResendIfNoNetwork = pendingMessagesEnabled
                )
            }
            .map { }
    }

    private suspend fun uploadAsset(
        conversationId: ConversationId,
        assetContent: AssetContent,
    ): Either<CoreFailure, AssetContent> = withContext(dispatchers.io) {
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
            ).flatMap { fetchAssetResult ->

                val updatedMetadata = metadata.withAudioNormalizedLoudnessIfNeeded(
                    mimeType = mimeType,
                    assetDataPath = fetchAssetResult.path.toString()
                )

                assetRepository.uploadAndPersistPrivateAsset(
                    mimeType = mimeType,
                    assetDataPath = fetchAssetResult.path,
                    otrKey = AES256Key(remoteData.otrKey),
                    extension = name?.fileExtension() ?: "",
                    conversationId = conversationId.toApi(),
                    filename = name,
                    filetype = mimeType,
                ).flatMap { uploadResult ->

                    val (uploadedAssetId, sha256Key) = uploadResult

                    assetContent.copy(
                        metadata = updatedMetadata,
                        remoteData = remoteData.copy(
                            sha256 = sha256Key.data,
                            assetId = uploadedAssetId.key,
                            assetDomain = uploadedAssetId.domain,
                            assetToken = uploadedAssetId.assetToken
                        )
                    ).right()
                }
            }
        }
    }

    private suspend fun AssetContent.AssetMetadata?.withAudioNormalizedLoudnessIfNeeded(
        mimeType: String,
        assetDataPath: String,
    ): AssetContent.AssetMetadata? = when {
        !isAudioMimeType(mimeType) -> this
        this !is AssetContent.AssetMetadata.Audio -> this
        normalizedLoudness != null -> this
        else -> {
            val normalizedLoudness = audioNormalizedLoudnessBuilder(assetDataPath)
            copy(normalizedLoudness = normalizedLoudness)
        }
    }

    private companion object {
        const val TYPE = "Asset"
    }
}
