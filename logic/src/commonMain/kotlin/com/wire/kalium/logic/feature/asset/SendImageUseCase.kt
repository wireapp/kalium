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

fun interface SendImageUseCase {
    /**
     * Function that enables sending an image as a private asset
     *
     * @param conversationId the id of the conversation where the asset wants to be sent
     * @param assetData the raw data of the image to be uploaded to the backend and sent to the given conversation
     * @return an [Either] tuple containing a [CoreFailure] in case anything goes wrong and [Unit] in case everything succeeds
     */
    suspend operator fun invoke(conversationId: ConversationId, imageRawData: ByteArray): SendImageMessageResult
}

internal class SendImageUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val clientRepository: ClientRepository,
    private val assetDataSource: AssetRepository,
    private val userRepository: UserRepository,
    private val messageSender: MessageSender
) : SendImageUseCase {

    override suspend fun invoke(conversationId: ConversationId, imageRawData: ByteArray): SendImageMessageResult = suspending {
        // Encrypt the asset data with the provided otr key
        val otrKey = generateRandomAES256Key()
        val encryptedData = encryptDataWithAES256(PlainData(imageRawData))

        // Calculate the SHA
        val sha256 = calcSHA256(encryptedData.data)

        // Upload the asset encrypted data
        assetDataSource.uploadAndPersistPrivateAsset(ImageAsset.JPEG, encryptedData.data).flatMap { assetId ->
            // Try to send the AssetMessage
            prepareAndSendAssetMessage(conversationId, imageRawData.size, sha256, otrKey, assetId).flatMap {
                Either.Right(Unit)
            }
        }.coFold({
            kaliumLogger.e("Something went wrong when sending the Image Message")
            SendImageMessageResult.Failure(it)
        }, {
            SendImageMessageResult.Success
        })
    }

    private suspend fun prepareAndSendAssetMessage(
        conversationId: ConversationId,
        dataSize: Int,
        sha256: String,
        otrKey: AES256Key,
        assetId: UploadedAssetId
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
                        assetId = assetId
                    )
                ),
                conversationId = conversationId,
                date = Clock.System.now().toString(),
                senderUserId = selfUser.id,
                senderClientId = currentClientId,
                status = Message.Status.PENDING
            )
            messageRepository.persistMessage(message) // Persist the asset message when the DB has been updated
        }.flatMap {
            messageSender.trySendingOutgoingMessage(conversationId, generatedMessageUuid)
        }.onFailure {
            kaliumLogger.e("There was an error when trying to send the asset on the conversation")
        }
    }

    private fun provideAssetMessageContent(dataSize: Int, sha256: String, otrKey: AES256Key, assetId: UploadedAssetId): AssetContent {
        return AssetContent(
            size = dataSize,
            name = "",
            mimeType = ImageAsset.JPEG.name,
            metadata = AssetContent.AssetMetadata.Image(0, 0),
            remoteData = AssetContent.RemoteData(
                otrKey = otrKey.data,
                sha256 = sha256.toByteArray(),
                assetId = assetId.key,
                encryptionAlgorithm = AssetContent.RemoteData.EncryptionAlgorithm.AES_CBC,
                assetDomain = null,  // TODO: fill in the assetDomain, it's returned by the BE when uploading an asset.
                assetToken = null
            )
        )
    }
}

sealed class SendImageMessageResult {
    object Success : SendImageMessageResult()
    class Failure(val coreFailure: CoreFailure) : SendImageMessageResult()
}
