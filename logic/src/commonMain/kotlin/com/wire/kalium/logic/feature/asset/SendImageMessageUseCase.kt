package com.wire.kalium.logic.feature.asset

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.*
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.ImageAsset
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.kaliumLogger
import io.ktor.utils.io.core.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

fun interface SendImageMessageUseCase {
    /**
     * Function that enables sending an image as a private asset
     *
     * @param conversationId the id of the conversation where the asset wants to be sent
     * @param assetData the raw data of the image to be uploaded to the backend and sent to the given conversation
     * @return an [Either] tuple containing a [CoreFailure] in case anything goes wrong and [Unit] in case everything succeeds
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        imageRawData: ByteArray,
        imgWidth: Int,
        imgHeight: Int
    ): SendImageMessageResult
}

internal class SendImageMessageUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val clientRepository: ClientRepository,
    private val assetDataSource: AssetRepository,
    private val userRepository: UserRepository,
    private val messageSender: MessageSender
) : SendImageMessageUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        imageRawData: ByteArray,
        imgWidth: Int,
        imgHeight: Int
    ): SendImageMessageResult = suspending {
        // Encrypt the asset data with the provided otr key
        val otrKey = generateRandomAES256Key()
        val encryptedData = encryptDataWithAES256(PlainData(imageRawData), otrKey)

        // Calculate the SHA of the encrypted data
        val sha256 = calcSHA256(encryptedData.data)

        // Upload the asset encrypted data
        assetDataSource.uploadAndPersistPrivateAsset(ImageAsset.JPEG, encryptedData.data).flatMap { assetId ->
            // Try to send the AssetMessage
            prepareAndSendImageMessage(conversationId, imageRawData.size, sha256, otrKey, assetId, imgWidth, imgHeight).flatMap {
                Either.Right(Unit)
            }
        }.coFold({
            kaliumLogger.e("Something went wrong when sending the Image Message")
            SendImageMessageResult.Failure(it)
        }, {
            SendImageMessageResult.Success
        })
    }

    private suspend fun prepareAndSendImageMessage(
        conversationId: ConversationId,
        dataSize: Int,
        sha256: ByteArray,
        otrKey: AES256Key,
        assetId: UploadedAssetId,
        imgWidth: Int,
        imgHeight: Int
    ) = suspending {
        // Get my current user
        val selfUser = userRepository.getSelfUser().first()

        // Prepare the Image Message
        val generatedMessageUuid = uuid4().toString()

        clientRepository.currentClientId().flatMap { currentClientId ->
            val message = Message(
                id = generatedMessageUuid,
                content = MessageContent.Asset(
                    provideAssetMessageContent(
                        dataSize = dataSize,
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
                status = Message.Status.PENDING
            )
            messageRepository.persistMessage(message)
        }.flatMap {
            messageSender.trySendingOutgoingMessageById(conversationId, generatedMessageUuid)
        }.onFailure {
            kaliumLogger.e("There was an error when trying to send the asset on the conversation")
        }
    }

    private fun provideAssetMessageContent(
        dataSize: Int,
        sha256: ByteArray,
        otrKey: AES256Key,
        assetId: UploadedAssetId,
        imgWidth: Int,
        imgHeight: Int
    ): AssetContent {
        return AssetContent(
            size = dataSize,
            name = "",
            mimeType = ImageAsset.JPEG.name,
            metadata = AssetContent.AssetMetadata.Image(imgWidth, imgHeight),
            remoteData = AssetContent.RemoteData(
                otrKey = otrKey.data,
                sha256 = sha256,
                assetId = assetId.key,
                encryptionAlgorithm = AssetContent.RemoteData.EncryptionAlgorithm.AES_CBC,
                assetDomain = null,  // TODO: fill in the assetDomain, it's returned by the BE when uploading an asset.
                assetToken = assetId.assetToken
            )
        )
    }
}

sealed class SendImageMessageResult {
    object Success : SendImageMessageResult()
    class Failure(val coreFailure: CoreFailure) : SendImageMessageResult()
}
