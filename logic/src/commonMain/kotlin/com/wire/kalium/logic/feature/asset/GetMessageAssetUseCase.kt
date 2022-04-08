package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.kaliumLogger

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
    ): MessageAssetResult = suspending {
        messageRepository.getMessageById(conversationId = conversationId, messageUuid = messageId).coFold({
            kaliumLogger.e("There was an error retrieving the asset message $messageId")
            MessageAssetResult.Failure(it)
        }, { message ->
            val (assetKey, assetToken, encryptionKey) = when (val content = message.content) {
                is MessageContent.Asset -> {
                    with(content.value.remoteData) {
                        Triple(assetId, assetToken, otrKey)
                    }
                }
                // This should never happen
                else -> return@coFold MessageAssetResult.Failure(
                    CoreFailure.Unknown(IllegalStateException("The message associated to this id, was not an asset message"))
                )
            }
            assetDataSource.downloadPrivateAsset(assetKey = assetKey, assetToken = assetToken).coFold({
                kaliumLogger.e("There was an error downloading asset with id => $assetKey")
                MessageAssetResult.Failure(it)
            }, { encodedAsset ->
                val rawAsset = decryptDataWithAES256(EncryptedData(encodedAsset), AES256Key(encryptionKey)).data
                MessageAssetResult.Success(rawAsset)
            })
        })
    }
}

sealed class MessageAssetResult {
    class Success(val decodedAsset: ByteArray) : MessageAssetResult()
    class Failure(val coreFailure: CoreFailure) : MessageAssetResult()
}
