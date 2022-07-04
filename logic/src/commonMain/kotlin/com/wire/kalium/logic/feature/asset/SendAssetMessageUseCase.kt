package com.wire.kalium.logic.feature.asset

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.asset.isValidImage
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
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.logic.util.fileExtensionToAssetType
import com.wire.kalium.logic.util.isGreaterThan
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import okio.Path

fun interface SendAssetMessageUseCase {
    /**
     * Function that enables sending an asset message
     *
     * @param conversationId the id of the conversation where the asset wants to be sent
     * @param assetDataPath the raw data of the asset to be uploaded to the backend and sent to the given conversation
     * @param assetDataSize the size of the original asset file
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
        assetWidth: Int?,
        assetHeight: Int?
    ): SendAssetMessageResult
}

internal class SendAssetMessageUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val clientRepository: ClientRepository,
    private val assetDataSource: AssetRepository,
    private val userRepository: UserRepository,
    private val messageSender: MessageSender
) : SendAssetMessageUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        assetDataPath: Path,
        assetDataSize: Long,
        assetName: String,
        assetMimeType: String,
        assetWidth: Int?,
        assetHeight: Int?
    ): SendAssetMessageResult {

        // Generate the otr asymmetric key that will be used to encrypt the data
        val otrKey = generateRandomAES256Key()

        // The assetDataSource will encrypt the data with the provided otrKey and upload it if successful
        return assetDataSource.uploadAndPersistPrivateAsset(
            assetName.fileExtension().fileExtensionToAssetType(),
            assetDataPath,
            otrKey
        ).flatMap { (assetId, sha256) ->

            // Try to send the Asset Message
            prepareAndSendAssetMessage(
                conversationId,
                assetDataSize,
                assetName,
                assetMimeType,
                sha256.data,
                otrKey,
                assetId,
                assetWidth,
                assetHeight
            ).flatMap {
                Either.Right(Unit)
            }
        }.fold({
            kaliumLogger.e("Something went wrong when sending the Asset Message")
            SendAssetMessageResult.Failure(it)
        }, {
            SendAssetMessageResult.Success
        })
    }

    @Suppress("LongParameterList")
    private suspend fun prepareAndSendAssetMessage(
        conversationId: ConversationId,
        dataSize: Long,
        assetName: String?,
        assetMimeType: String,
        sha256: ByteArray,
        otrKey: AES256Key,
        assetId: UploadedAssetId,
        assetWidth: Int?,
        assetHeight: Int?
    ): Either<CoreFailure, Unit> = clientRepository.currentClientId().flatMap { currentClientId ->
        // Get my current user
        val selfUser = userRepository.observeSelfUser().first()

        // Create a unique message ID
        val generatedMessageUuid = uuid4().toString()

        val message = Message.Regular(
            id = generatedMessageUuid,
            content = MessageContent.Asset(
                provideAssetMessageContent(
                    dataSize = dataSize,
                    assetName = assetName,
                    mimeType = assetMimeType,
                    sha256 = sha256,
                    otrKey = otrKey,
                    assetId = assetId,
                    assetWidth = assetWidth,
                    assetHeight = assetHeight,
                )
            ),
            conversationId = conversationId,
            date = Clock.System.now().toString(),
            senderUserId = selfUser.id,
            senderClientId = currentClientId,
            status = Message.Status.PENDING,
            editStatus = Message.EditStatus.NotEdited
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
    assetWidth: Int?,
    assetHeight: Int?
): AssetContent = AssetContent(
    sizeInBytes = dataSize,
    name = assetName,
    mimeType = mimeType,
    metadata = when {
        isValidImage(mimeType) && (assetHeight.isGreaterThan(0) && (assetWidth.isGreaterThan(0))) -> {
            AssetContent.AssetMetadata.Image(assetWidth, assetHeight)
        }
        else -> null
    },
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

sealed class SendAssetMessageResult {
    object Success : SendAssetMessageResult()
    class Failure(val coreFailure: CoreFailure) : SendAssetMessageResult()
}
