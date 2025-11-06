/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.asset.upload

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.isAudioMimeType
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mockable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

@Mockable
internal interface UploadAssetUseCase {
    suspend operator fun invoke(message: Message.Regular, metadata: UploadAssetMessageMetadata): Either<CoreFailure, Unit>
}

internal class UploadAssetUseCaseImpl(
    private val assetDataSource: AssetRepository,
    private val messageSender: MessageSender,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
    private val persistMessage: PersistMessageUseCase,
    private val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder,
    private val dispatcher: KaliumDispatcher
) : UploadAssetUseCase {

    private suspend fun getOrBuildAudioNormalizedLoudnessIfNeeded(metadata: UploadAssetMessageMetadata): ByteArray? = when {
        !isAudioMimeType(metadata.mimeType) -> null
        metadata.audioNormalizedLoudness != null -> metadata.audioNormalizedLoudness
        else -> audioNormalizedLoudnessBuilder(metadata.assetDataPath.toString())
    }

    override suspend fun invoke(message: Message.Regular, metadata: UploadAssetMessageMetadata) = withContext(dispatcher.io) {

        updateAssetMessageTransferStatus(AssetTransferStatus.UPLOAD_IN_PROGRESS, message.conversationId, message.id)

        val audioNormalizedLoudnessDeferred = async { getOrBuildAudioNormalizedLoudnessIfNeeded(metadata) }

        assetDataSource.uploadAndPersistPrivateAsset(
            mimeType = metadata.mimeType,
            assetDataPath = metadata.assetDataPath,
            otrKey = metadata.otrKey,
            extension = metadata.assetName.fileExtension(),
            conversationId = message.conversationId.toApi(),
            filename = metadata.assetName,
            filetype = metadata.mimeType,
        ).onFailure {
            updateAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD, message.conversationId, message.id)
            messageSendFailureHandler.handleFailureAndUpdateMessageStatus(it, message.conversationId, message.id, TYPE)
        }.onSuccess {
            updateAssetMessageTransferStatus(AssetTransferStatus.UPLOADED, message.conversationId, message.id)
            // We delete asset added temporarily that was used to show the loading
            assetDataSource.deleteAssetLocally(metadata.assetId.key)
        }.flatMap { (assetId, sha256) ->
            // We update the message with the remote data (assetId & sha256 key) obtained by the successful asset upload,
            // we also update the generated audio normalized loudness if applicable,
            // and we persist and update the message on the DB layer to display the changes on the Conversation screen
            val updatedAssetMessageContent = metadata.copy(
                sha256Key = sha256,
                assetId = assetId,
                audioNormalizedLoudness = audioNormalizedLoudnessDeferred.await()
            )
            val updatedMessage = message.copy(
                content = MessageContent.Asset(
                    value = updatedAssetMessageContent.toAssetContent()
                ),
                expectsReadConfirmation = message.expectsReadConfirmation
            )
            persistMessage(updatedMessage)
                .onFailure {
                    kaliumLogger.e(
                        "There was an error when trying to persist the updated asset message with the information returned by the backend"
                    )
                }.onSuccess {
                    // Finally we try to send the Asset Message to the recipients of the given conversation
                    messageSender.sendMessage(updatedMessage).onFailure {
                        messageSendFailureHandler.handleFailureAndUpdateMessageStatus(it, message.conversationId, message.id, TYPE)
                    }
                }
        }
    }

    private companion object {
        const val TYPE = "Asset"
    }
}
