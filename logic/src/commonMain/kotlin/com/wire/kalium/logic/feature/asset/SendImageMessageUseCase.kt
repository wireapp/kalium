package com.wire.kalium.logic.feature.asset

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.ImageAsset
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

fun interface SendImageMessageUseCase {
    /**
     * Function that enables sending an image as a private asset
     *
     * @param conversationId the id of the conversation where the asset wants to be sent
     * @param imageRawData the raw data of the image to be uploaded to the backend and sent to the given conversation
     * @param imageName the name of the original image file
     * @param imgWidth the image width in pixels
     * @param imgHeight the image height in pixels
     * @return an [Either] tuple containing a [CoreFailure] in case anything goes wrong and [Unit] in case everything succeeds
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        imageRawData: ByteArray,
        imageName: String?,
        imgWidth: Int,
        imgHeight: Int
    ): SendImageMessageResult
}

internal class SendImageMessageUseCaseImpl(
    private val persistMessage: PersistMessageUseCase,
    private val clientRepository: ClientRepository,
    private val assetDataSource: AssetRepository,
    private val userRepository: UserRepository,
    private val messageSender: MessageSender
) : SendImageMessageUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        imageRawData: ByteArray,
        imageName: String?,
        imgWidth: Int,
        imgHeight: Int
    ): SendImageMessageResult {
        // Encrypt the asset data with the provided otr key
        val otrKey = generateRandomAES256Key()
        val encryptedData = encryptDataWithAES256(PlainData(imageRawData), otrKey)

        // Calculate the SHA of the encrypted data
        val sha256 = calcSHA256(encryptedData.data)

        // Upload the asset encrypted data
        return assetDataSource.uploadAndPersistPrivateAsset(ImageAsset.JPEG, encryptedData.data).flatMap { assetId ->
            // Try to send the Image Message
            prepareAndSendImageMessage(
                conversationId, imageRawData.size.toLong(),
                imageName, sha256, otrKey, assetId, imgWidth, imgHeight
            )
        }.fold({
            kaliumLogger.e("Something went wrong when sending the Image Message")
            SendImageMessageResult.Failure(it)
        }, {
            SendImageMessageResult.Success
        })
    }

    @Suppress("LongParameterList")
    private suspend fun prepareAndSendImageMessage(
        conversationId: ConversationId,
        dataSize: Long,
        imageName: String?,
        sha256: ByteArray,
        otrKey: AES256Key,
        assetId: UploadedAssetId,
        imgWidth: Int,
        imgHeight: Int
    ): Either<CoreFailure, Unit> {
        // Get my current user
        val selfUser = userRepository.observeSelfUser().first()

        // Create a unique image message ID
        val generatedMessageUuid = uuid4().toString()

        return clientRepository.currentClientId().flatMap { currentClientId ->
            val message = Message.Regular(
                id = generatedMessageUuid,
                content = MessageContent.Asset(
                    provideAssetMessageContent(
                        dataSize = dataSize,
                        imageName = imageName,
                        sha256 = sha256,
                        otrKey = otrKey,
                        assetId = assetId,
                        imgWidth = imgWidth,
                        imgHeight = imgHeight
                    )
                ),
                conversationId = conversationId,
                date = Clock.System.now().toString(),
                senderUserId = selfUser.id,
                senderClientId = currentClientId,
                status = Message.Status.PENDING,
                editStatus = Message.EditStatus.NotEdited
            )
            persistMessage(message)
        }.flatMap {
            messageSender.sendPendingMessage(conversationId, generatedMessageUuid)
        }.onFailure {
            kaliumLogger.e("There was an error when trying to send the image message to the conversation")
        }
    }

    @Suppress("LongParameterList")
    private fun provideAssetMessageContent(
        dataSize: Long,
        imageName: String?,
        sha256: ByteArray,
        otrKey: AES256Key,
        assetId: UploadedAssetId,
        imgWidth: Int,
        imgHeight: Int
    ): AssetContent {
        return AssetContent(
            sizeInBytes = dataSize,
            name = imageName,
            mimeType = ImageAsset.JPEG.name,
            metadata = AssetContent.AssetMetadata.Image(imgWidth, imgHeight),
            remoteData = AssetContent.RemoteData(
                otrKey = otrKey.data,
                sha256 = sha256,
                assetId = assetId.key,
                encryptionAlgorithm = MessageEncryptionAlgorithm.AES_CBC,
                assetDomain = assetId.domain,
                assetToken = assetId.assetToken
            ),
            // Asset is already in our local storage and therefore accessible but until we don't save it to external storage the asset
            // will only be treated as "SAVED_INTERNALLY"
            downloadStatus = Message.DownloadStatus.SAVED_INTERNALLY
        )
    }
}

sealed class SendImageMessageResult {
    object Success : SendImageMessageResult()
    class Failure(val coreFailure: CoreFailure) : SendImageMessageResult()
}
