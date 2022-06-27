package com.wire.kalium.logic.feature.asset

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.EncryptionFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.ImageAsset
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.logic.util.fileExtensionToAssetType
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okio.Path

fun interface SendImageMessageUseCase {
    /**
     * Function that enables sending an image as a private asset
     *
     * @param conversationId the id of the conversation where the asset wants to be sent
     * @param imageDataPath the [Path] of the image to be uploaded to the backend and sent to the given conversation
     * @param imageName the name of the original image file
     * @param imgWidth the image width in pixels
     * @param imgHeight the image height in pixels
     * @return an [Either] tuple containing a [CoreFailure] in case anything goes wrong and [Unit] in case everything succeeds
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        imageDataPath: Path,
        imageName: String,
        imgWidth: Int,
        imgHeight: Int
    ): SendImageMessageResult
}

internal class SendImageMessageUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val clientRepository: ClientRepository,
    private val assetDataSource: AssetRepository,
    private val userRepository: UserRepository,
    private val messageSender: MessageSender,
    private val kaliumFileSystem: KaliumFileSystem
) : SendImageMessageUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        imageDataPath: Path,
        imageName: String,
        imgWidth: Int,
        imgHeight: Int
    ): SendImageMessageResult = withContext(KaliumDispatcherImpl.io) {
        // Encrypt the asset data with the provided otr key
        val otrKey = generateRandomAES256Key()
        val encryptedDataPath = kaliumFileSystem.tempFilePath("temp_encrypted.aes")
        if (kaliumFileSystem.exists(encryptedDataPath)) kaliumFileSystem.delete(encryptedDataPath)
        val encryptedDataSize = encryptDataWithAES256(imageDataPath, otrKey, encryptedDataPath, kaliumFileSystem)
        val encryptedDataSucceeded = encryptedDataSize > 0L

        return@withContext if (encryptedDataSucceeded) {
            // Calculate the SHA of the encrypted data
            val sha256 = calcSHA256(encryptedDataPath, kaliumFileSystem)
                    ?: run { return@withContext SendImageMessageResult.Failure(EncryptionFailure()) }

            // Upload the asset encrypted data
            return@withContext assetDataSource.uploadAndPersistPrivateAsset(
                imageName.fileExtension().fileExtensionToAssetType(),
                encryptedDataPath,
                encryptedDataSize
            ).flatMap { assetId ->
                // Try to send the Image Message
                prepareAndSendImageMessage(conversationId, encryptedDataSize, imageName, sha256, otrKey, assetId, imgWidth, imgHeight)
            }.fold({
                kaliumLogger.e("Something went wrong when sending the Image Message")
                SendImageMessageResult.Failure(it)
            }, {
                SendImageMessageResult.Success
            })
        } else {
            kaliumLogger.e("Something went wrong when encrypting the Image Asset Message")
            SendImageMessageResult.Failure(EncryptionFailure())
        }
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
            messageRepository.persistMessage(message)
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
            mimeType = ImageAsset.JPEG.mimeType,
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
