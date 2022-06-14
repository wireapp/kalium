package com.wire.kalium.logic.feature.asset

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.EncryptionFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.asset.FileAsset
import com.wire.kalium.logic.data.asset.KaliumFileSystem
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
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.fileExtension
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import okio.Path
import okio.Path.Companion.toPath

fun interface SendAssetMessageUseCase {
    /**
     * Function that enables sending an asset message
     *
     * @param conversationId the id of the conversation where the asset wants to be sent
     * @param assetDataPath the raw data of the asset to be uploaded to the backend and sent to the given conversation
     * @param assetName the name of the original asset file
     * @param assetMimeType the type of the asset file
     * @return an [Either] tuple containing a [CoreFailure] in case anything goes wrong and [Unit] in case everything succeeds
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        assetDataPath: Path,
        assetDataSize: Long,
        assetName: String,
        assetMimeType: String,
    ): SendAssetMessageResult
}

internal class SendAssetMessageUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val clientRepository: ClientRepository,
    private val assetDataSource: AssetRepository,
    private val userRepository: UserRepository,
    private val messageSender: MessageSender,
    private val kaliumFileSystem: KaliumFileSystem
) : SendAssetMessageUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        assetDataPath: Path,
        assetDataSize: Long,
        assetName: String,
        assetMimeType: String
    ): SendAssetMessageResult {
        // Encrypt the asset data with the provided otr key
        val otrKey = generateRandomAES256Key()
        val encryptedDataPath = kaliumFileSystem.tempFilePath("temp_encrypted.aes")
        val encryptionSucceeded = encryptDataWithAES256(assetDataPath, otrKey, encryptedDataPath, kaliumFileSystem)

        return if (encryptionSucceeded) {
            // Calculate the SHA of the encrypted data
            val sha256 = calcSHA256(encryptedDataPath)

            // Upload the asset encrypted data and force the mimeType to be a File
            assetDataSource.uploadAndPersistPrivateAsset(
                FileAsset(fileExtension = assetName.fileExtension()),
                encryptedDataPath,
                assetDataSize
            ).flatMap { assetId ->
                // Try to send the Asset Message
                prepareAndSendAssetMessage(
                    conversationId,
                    assetDataSize,
                    assetName,
                    assetMimeType,
                    sha256,
                    otrKey,
                    assetId
                ).flatMap {
                    Either.Right(Unit)
                }
            }.fold({
                kaliumLogger.e("Something went wrong when sending the Asset Message")
                SendAssetMessageResult.Failure(it)
            }, {
                // TODO remove the temp path for large assets?
                SendAssetMessageResult.Success
            })
        } else {
            kaliumLogger.e("Something went wrong when encrypting the Asset Message")
            SendAssetMessageResult.Failure(EncryptionFailure())
        }
    }

    @Suppress("LongParameterList")
    private suspend fun prepareAndSendAssetMessage(
        conversationId: ConversationId,
        dataSize: Long,
        assetName: String?,
        assetMimeType: String,
        sha256: ByteArray,
        otrKey: AES256Key,
        assetId: UploadedAssetId
    ): Either<CoreFailure, Unit> = clientRepository.currentClientId().flatMap { currentClientId ->
        // Get my current user
        val selfUser = userRepository.getSelfUser().first()

        // Create a unique message ID
        val generatedMessageUuid = uuid4().toString()

        val message = Message(
            id = generatedMessageUuid,
            content = MessageContent.Asset(
                provideAssetMessageContent(
                    dataSize = dataSize,
                    assetName = assetName,
                    mimeType = assetMimeType,
                    sha256 = sha256,
                    otrKey = otrKey,
                    assetId = assetId,
                )
            ),
            conversationId = conversationId,
            date = Clock.System.now().toString(),
            senderUserId = selfUser.id,
            senderClientId = currentClientId,
            status = Message.Status.PENDING
        )
        messageRepository.persistMessage(message).map { message }
    }.flatMap { message ->
        messageSender.sendPendingMessage(conversationId, message.id)
    }.onFailure {
        kaliumLogger.e("There was an error when trying to send the asset on the conversation")
    }
}

@Suppress("LongParameterList")
private fun provideAssetMessageContent(
    dataSize: Long,
    assetName: String?,
    sha256: ByteArray,
    otrKey: AES256Key,
    assetId: UploadedAssetId,
    mimeType: String,
): AssetContent = AssetContent(
    sizeInBytes = dataSize,
    name = assetName,
    mimeType = mimeType,
    remoteData = AssetContent.RemoteData(
        otrKey = otrKey.data,
        sha256 = sha256,
        assetId = assetId.key,
        encryptionAlgorithm = AssetContent.RemoteData.EncryptionAlgorithm.AES_CBC,
        assetDomain = null,  // TODO(assets): fill in the assetDomain, it's returned by the BE when uploading an asset.
        assetToken = assetId.assetToken
    ),
    // Asset is already in our local storage and therefore accessible but until we don't save it to external storage the asset
    // will only be treated as "SAVED_INTERNALLY"
    downloadStatus = Message.DownloadStatus.SAVED_INTERNALLY
)

sealed class SendAssetMessageResult {
    object Success : SendAssetMessageResult()
    class Failure(val coreFailure: CoreFailure) : SendAssetMessageResult()
}
