package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import okio.Path

interface GetMessageAssetUseCase {
    /**
     * Function that enables fetching a message asset locally or if it doesn't exist, downloading and decrypting it as a ByteArray
     *
     * @param conversationId the conversation ID the asset message belongs to
     * @param messageId the message Identifier
     * @return [PublicAssetResult] with a [ByteArray] in case of success or [CoreFailure] on failure
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String,
    ): MessageAssetResult
}

internal class GetMessageAssetUseCaseImpl(
    private val assetDataSource: AssetRepository,
    private val messageRepository: MessageRepository
) : GetMessageAssetUseCase {
    override suspend fun invoke(
        conversationId: ConversationId,
        messageId: String
    ): MessageAssetResult =
        messageRepository.getMessageById(conversationId = conversationId, messageUuid = messageId).fold({
            kaliumLogger.e("There was an error retrieving the asset message $messageId")
            MessageAssetResult.Failure(it)
        }, { message ->
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
                else -> return@fold MessageAssetResult.Failure(
                    CoreFailure.Unknown(IllegalStateException("The message associated to this id, was not an asset message"))
                )
            }
            assetDataSource.fetchPrivateDecodedAsset(
                assetId = AssetId(assetMetadata.assetKey, assetMetadata.assetKeyDomain.orEmpty()),
                assetToken = assetMetadata.assetToken,
                encryptionKey = assetMetadata.encryptionKey
            ).fold({
                kaliumLogger.e("There was an error downloading asset with id => ${assetMetadata.assetKey}")
                MessageAssetResult.Failure(it)
            }, { decodedAssetPath ->
                MessageAssetResult.Success(decodedAssetPath, assetMetadata.assetSize)
            })
        })
}

sealed class MessageAssetResult {
    class Success(val decodedAssetPath: Path, val assetSize: Long) : MessageAssetResult()
    class Failure(val coreFailure: CoreFailure) : MessageAssetResult()
}
