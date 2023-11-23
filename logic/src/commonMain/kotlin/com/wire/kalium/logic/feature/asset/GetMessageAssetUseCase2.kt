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

package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.Message.DownloadStatus.SAVED_EXTERNALLY
import com.wire.kalium.logic.data.message.Message.DownloadStatus.SAVED_INTERNALLY
import com.wire.kalium.logic.data.message.Message.UploadStatus.NOT_UPLOADED
import com.wire.kalium.logic.data.message.Message.UploadStatus.UPLOADED
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNotFound
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.withContext
import okio.Path

interface GetMessageAssetUseCase2 { // TODO rename
    /**
     * Function that enables fetching a message asset locally or if it doesn't exist, downloading it from the server, decrypting it and
     * saving it locally.
     * @param conversationId the conversation ID the asset message belongs to
     * @param messageId the message Identifier
     * @return [MessageAssetResult] with the [Path] and size of the decrypted asset in case of success or [CoreFailure] if any
     * failure occurred.
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String,
    ): MessageAssetResult
}

// TODO: refactor this use case or find a way to centralize [Message.DownloadStatus] management
internal class GetMessageAssetUseCaseImpl2(
    private val assetDataSource: AssetRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val updateAssetMessageDownloadStatus: UpdateAssetMessageDownloadStatusUseCase,
    private val dispatcher: KaliumDispatcher
) : GetMessageAssetUseCase2 {

    @Suppress("LongMethod", "ComplexMethod")
    override suspend fun invoke(
        conversationId: ConversationId,
        messageId: String
    ): MessageAssetResult = withContext(dispatcher.io) {
        messageRepository.getMessageById(conversationId = conversationId, messageUuid = messageId).fold({
            kaliumLogger.e("There was an error retrieving the asset message ${messageId.obfuscateId()}")
            MessageAssetResult.Failure(it, false)
        }, { message ->
            when (val content = message.content) {
                is MessageContent.Asset -> {
                    val assetDownloadStatus = content.value.downloadStatus
                    val assetUploadStatus = content.value.uploadStatus
                    val wasDownloaded: Boolean = assetDownloadStatus == SAVED_INTERNALLY || assetDownloadStatus == SAVED_EXTERNALLY
                    // assets uploaded by other clients have upload status NOT_UPLOADED
                    val alreadyUploaded: Boolean = (assetUploadStatus == NOT_UPLOADED && content.value.shouldBeDisplayed)
                            || assetUploadStatus == UPLOADED
                    val assetMetadata = with(content.value.remoteData) {
                        DownloadAssetMessageMetadata(
                            content.value.name ?: "",
                            content.value.sizeInBytes,
                            assetId,
                            assetDomain,
                            assetToken,
                            AES256Key(otrKey),
                            SHA256Key(sha256)
                        )
                    }

                    // Start progress bar for generic assets
                    if (!wasDownloaded && alreadyUploaded)
                        updateAssetMessageDownloadStatus(Message.DownloadStatus.DOWNLOAD_IN_PROGRESS, conversationId, messageId, null)

                    assetDataSource.fetchPrivateDecodedAsset(
                        assetId = assetMetadata.assetKey,
                        assetDomain = assetMetadata.assetKeyDomain,
                        assetName = assetMetadata.assetName,
                        mimeType = content.value.mimeType,
                        assetToken = assetMetadata.assetToken,
                        encryptionKey = assetMetadata.encryptionKey,
                        assetSHA256Key = assetMetadata.assetSHA256Key,
                        downloadIfNeeded = alreadyUploaded
                    ).fold({
                        kaliumLogger.e("There was an error downloading asset with id => ${assetMetadata.assetKey.obfuscateId()}")
                        // This should be called if there is an issue while downloading the asset

                        if (it is NetworkFailure.ServerMiscommunication) {
                            if (it.kaliumException is KaliumException.InvalidRequestError && it.kaliumException.isNotFound()) {
                                kaliumLogger.d("KBX update status not found for asset ${assetMetadata.assetKey}")
                                updateAssetMessageDownloadStatus(Message.DownloadStatus.NOT_FOUND, conversationId, messageId, null)
                            }
                        } else {
                            updateAssetMessageDownloadStatus(Message.DownloadStatus.FAILED_DOWNLOAD, conversationId, messageId, null)
                        }
                        when {
                            it.isInvalidRequestError -> {
                                assetMetadata.assetKeyDomain?.let { domain ->
                                    userRepository.removeUserBrokenAsset(QualifiedID(assetMetadata.assetKey, domain))
                                }
                                MessageAssetResult.Failure(it, false)
                            }

                            it is NetworkFailure.FederatedBackendFailure -> MessageAssetResult.Failure(it, false)
                            it is NetworkFailure.NoNetworkConnection -> MessageAssetResult.Failure(it, true)
                            else -> MessageAssetResult.Failure(it, true)
                        }
                    }, { decodedAssetPath ->
                        // Only update the asset download status if it wasn't downloaded before, aka the asset was indeed downloaded
                        // while running this specific use case. Otherwise, recursive loop as described above kicks in.
                        if (!wasDownloaded)
                            updateAssetMessageDownloadStatus(
                                Message.DownloadStatus.SAVED_INTERNALLY,
                                conversationId,
                                messageId,
                                decodedAssetPath.toString()
                            )

                        MessageAssetResult.Success(decodedAssetPath, assetMetadata.assetSize, assetMetadata.assetName)
                    })
                }

                // This should never happen
                else -> MessageAssetResult.Failure(
                    CoreFailure.Unknown(IllegalStateException("The message associated to this id, was not an asset message")),
                    isRetryNeeded = false
                )
            }
        })
    }
}
