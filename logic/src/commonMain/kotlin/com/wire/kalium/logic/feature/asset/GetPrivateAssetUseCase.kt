package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.kaliumLogger

interface GetPrivateAssetUseCase {
    /**
     * Function that enables fetching a message asset locally or if it doesn't exist, downloading and decrypting it as a ByteArray
     *
     * @param assetKey the asset identifier
     * @param assetToken the asset token used to provide an extra layer of asset/user authentication
     * @param conversationId the conversation ID the asset message belongs to
     * @param encryptionKey the private encryption key needed to decrypt the message once it has been downloaded
     * @return [PublicAssetResult] with a [ByteArray] in case of success or [CoreFailure] on failure
     */
    suspend operator fun invoke(
        assetKey: UserAssetId,
        assetToken: String,
        conversationId: ConversationId,
        messageId: String,
        encryptionKey: ByteArray
    ): PrivateAssetResult
}

internal class GetPrivateAssetUseCaseImpl(private val assetDataSource: AssetRepository) : GetPrivateAssetUseCase {
    override suspend fun invoke(
        assetKey: UserAssetId,
        assetToken: String,
        conversationId: ConversationId,
        messageId: String,
        encryptionKey: ByteArray
    ): PrivateAssetResult = suspending {
        // Download the encrypted asset
        assetDataSource.downloadPrivateAsset(assetKey, assetToken).coFold({
            kaliumLogger.e("There was an error downloading asset with id => $assetKey")
            PrivateAssetResult.Failure(it)
        }, { encodedAsset ->
            // Decrypt the asset data
            val rawAsset = decryptDataWithAES256(EncryptedData(encodedAsset), AES256Key(encryptionKey)).data
            PrivateAssetResult.Success(rawAsset)
        })
    }
}

sealed class PrivateAssetResult {
    class Success(val decodedAsset: ByteArray) : PrivateAssetResult()
    class Failure(val coreFailure: CoreFailure) : PrivateAssetResult()
}
