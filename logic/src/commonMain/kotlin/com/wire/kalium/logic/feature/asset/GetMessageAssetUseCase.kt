package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.Message.DownloadStatus.SAVED_EXTERNALLY
import com.wire.kalium.logic.data.message.Message.DownloadStatus.SAVED_INTERNALLY
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import okio.Path

interface GetMessageAssetUseCase {
    /**
     * Function that enables fetching a message asset locally or if it doesn't exist, downloading it from the server, decrypting it and
     * saving it locally. The function returns a [Deferred] result to the path where the decrypted asset was stored. The caller is
     * responsible for deciding whether to wait for the result or not.
     *
     * @param conversationId the conversation ID the asset message belongs to
     * @param messageId the message Identifier
     * @return a [Deferred] [MessageAssetResult] with the [Path] and size of the decrypted asset in case of success or [CoreFailure] if any
     * failure occurred.
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String,
    ): Deferred<MessageAssetResult>
}

// TODO: refactor this use case or find a way to centralize [Message.DownloadStatus] management
internal class GetMessageAssetUseCaseImpl(
    private val assetDataSource: AssetRepository,
    private val messageRepository: MessageRepository,
    private val updateAssetMessageDownloadStatus: UpdateAssetMessageDownloadStatusUseCase,
    private val scope: CoroutineScope,
    private val dispatcher: KaliumDispatcher
) : GetMessageAssetUseCase {
    override suspend fun invoke(
        conversationId: ConversationId,
        messageId: String
    ): Deferred<MessageAssetResult> =
        messageRepository.getMessageById(conversationId = conversationId, messageUuid = messageId).fold({
            kaliumLogger.e("There was an error retrieving the asset message ${messageId.obfuscateId()}")
            CompletableDeferred(MessageAssetResult.Failure(it))
        }, { message ->
            val assetDownloadStatus = (message.content as MessageContent.Asset).value.downloadStatus
            val wasDownloaded: Boolean = assetDownloadStatus == SAVED_INTERNALLY || assetDownloadStatus == SAVED_EXTERNALLY
            val assetMetadata = when (val content = message.content) {
                is MessageContent.Asset -> {
                    with(content.value.remoteData) {
                        DownloadAssetMessageMetadata(
                            content.value.name ?: "",
                            content.value.sizeInBytes,
                            assetId,
                            assetDomain,
                            assetToken,
                            AES256Key(otrKey)
                        )
                    }
                }
                // This should never happen
                else -> return@fold CompletableDeferred(MessageAssetResult.Failure(
                    CoreFailure.Unknown(IllegalStateException("The message associated to this id, was not an asset message"))
                ))
            }

            // Start progress bar for generic assets
            if (!wasDownloaded) updateAssetMessageDownloadStatus(Message.DownloadStatus.DOWNLOAD_IN_PROGRESS, conversationId, messageId)

            scope.async(dispatcher.io) {
                assetDataSource.fetchPrivateDecodedAsset(
                    assetId = AssetId(assetMetadata.assetKey, assetMetadata.assetKeyDomain.orEmpty()),
                    assetName = assetMetadata.assetName,
                    assetToken = assetMetadata.assetToken,
                    encryptionKey = assetMetadata.encryptionKey
                ).fold({
                    kaliumLogger.e("There was an error downloading asset with id => ${assetMetadata.assetKey.obfuscateId()}")
                    // This should be called if there is an issue while downloading the asset
                    updateAssetMessageDownloadStatus(Message.DownloadStatus.FAILED_DOWNLOAD, conversationId, messageId)
                    MessageAssetResult.Failure(it)
                }, { decodedAssetPath ->
                    // Only update the asset download status if it wasn't downloaded before, aka the asset was indeed downloaded while
                    // running this specific use case. Otherwise, recursive loop as described above kicks in.
                    if (!wasDownloaded)
                        updateAssetMessageDownloadStatus(Message.DownloadStatus.SAVED_INTERNALLY, conversationId, messageId)

                    MessageAssetResult.Success(decodedAssetPath, assetMetadata.assetSize)
                })
            }
        })
}

sealed class MessageAssetResult {
    class Success(val decodedAssetPath: Path, val assetSize: Long) : MessageAssetResult()
    class Failure(val coreFailure: CoreFailure) : MessageAssetResult()
}
