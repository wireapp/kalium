package com.wire.kalium.logic.feature.debug

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.asset.isValidImage
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.logic.util.isGreaterThan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import okio.Path

fun interface SendBrokenAssetMessageUseCase {
    /**
     * Function that enables sending an asset message to a given conversation
     *
     * @param conversationId the id of the conversation where the asset wants to be sent
     * @param assetDataPath the raw data of the asset to be uploaded to the backend and sent to the given conversation
     * @param assetDataSize the size of the original asset file
     * @param assetName the name of the original asset file
     * @param assetMimeType the type of the asset file
     * @return an [SendBrokenAssetMessageResult] containing a [CoreFailure] in case anything goes wrong and [Unit] in case everything succeeds
     */
    @Suppress("LongParameterList")
    suspend operator fun invoke(
        conversationId: ConversationId,
        assetDataPath: Path,
        assetDataSize: Long,
        assetName: String,
        assetMimeType: String,
        brokenState: BrokenState
    ): SendBrokenAssetMessageResult
}

@Suppress("LongParameterList")
internal class SendBrokenAssetMessageUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val assetDataSource: AssetRepository,
    private val userRepository: UserRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender
) : SendBrokenAssetMessageUseCase {
    private lateinit var currentAssetMessageContent: AssetMessageMetadata
    override suspend fun invoke(
        conversationId: ConversationId,
        assetDataPath: Path,
        assetDataSize: Long,
        assetName: String,
        assetMimeType: String,
        brokenState: BrokenState
    ): SendBrokenAssetMessageResult {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        // Generate the otr asymmetric key that will be used to encrypt the data
        val otrKey = generateRandomAES256Key()
        currentAssetMessageContent = AssetMessageMetadata(
            conversationId = conversationId,
            mimeType = assetMimeType,
            assetDataPath = assetDataPath,
            assetDataSize = assetDataSize,
            assetName = assetName,
            assetWidth = 0,
            assetHeight = 0,
            otrKey = otrKey,
            sha256Key = SHA256Key(ByteArray(DEFAULT_BYTE_ARRAY_SIZE)), // Sha256 will be replaced with right values after asset upload
            assetId = UploadedAssetId("", ""), // Asset ID will be replaced with right value after asset upload
        )
        lateinit var message: Message.Regular

        return clientRepository.currentClientId().flatMap { currentClientId ->
            // Get my current user
            val selfUser = userRepository.observeSelfUser().firstOrNull()

            if (selfUser == null) {
                kaliumLogger.e("There was an error obtaining the self user object :(")
                return@flatMap Either.Left(StorageFailure.DataNotFound)
            }

            // Create a unique message ID
            val generatedMessageUuid = uuid4().toString()

            message = Message.Regular(
                id = generatedMessageUuid,
                content = MessageContent.Asset(
                    provideAssetMessageContent(
                        currentAssetMessageContent,
                        Message.UploadStatus.UPLOAD_IN_PROGRESS, // We set UPLOAD_IN_PROGRESS when persisting the message for the first time
                        brokenState
                    )
                ),
                conversationId = conversationId,
                date = Clock.System.now().toString(),
                senderUserId = selfUser.id,
                senderClientId = currentClientId,
                status = Message.Status.PENDING,
                editStatus = Message.EditStatus.NotEdited
            )

            uploadAssetAndUpdateMessage(message, conversationId, brokenState)
        }.fold({
            SendBrokenAssetMessageResult.Failure(it)
        }, { SendBrokenAssetMessageResult.Success })
    }

    private suspend fun uploadAssetAndUpdateMessage(message: Message.Regular, conversationId: ConversationId, brokenState: BrokenState): Either<CoreFailure, Unit> =
        // The assetDataSource will encrypt the data with the provided otrKey and upload it if successful
        assetDataSource.uploadAndPersistPrivateAsset(
            currentAssetMessageContent.mimeType,
            currentAssetMessageContent.assetDataPath,
            currentAssetMessageContent.otrKey,
            currentAssetMessageContent.assetName.fileExtension()
        ).flatMap { (assetId, sha256) ->
            // We update the message with the remote data (assetId & sha256 key) obtained by the successful asset upload and we persist and
            // update the message on the DB layer to display the changes on the Conversation screen
            currentAssetMessageContent = currentAssetMessageContent.copy(sha256Key = sha256, assetId = assetId)
            val updatedMessage = message.copy(
                // We update the upload status to UPLOADED as the upload succeeded
                content = MessageContent.Asset(
                    provideAssetMessageContent(currentAssetMessageContent, Message.UploadStatus.UPLOADED, brokenState)
                )
            )
            prepareAndSendAssetMessage(message, conversationId)
        }.flatMap {
            Either.Right(Unit)
        }.onFailure {
            Either.Left(it)
        }

    @Suppress("LongParameterList")
    private suspend fun prepareAndSendAssetMessage(
        message: Message,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> =
        messageSender.sendPendingMessage(conversationId, message.id).onFailure {
            kaliumLogger.e("There was an error when trying to send the asset on the conversation")
        }

    @Suppress("LongParameterList", "MaxLineLength")
    private fun provideAssetMessageContent(
        assetMessageMetadata: AssetMessageMetadata,
        uploadStatus: Message.UploadStatus,
        brokenState: BrokenState,
    ): AssetContent {
        with(assetMessageMetadata) {
            val manipulatedSha256KeyData = if (brokenState.invalidHash) {
                ByteArray(1)
            } else if (brokenState.otherHash) {
                SHA256Key(ByteArray(DEFAULT_BYTE_ARRAY_SIZE)).data
            } else {
                sha256Key.data
            }
            return AssetContent(
                sizeInBytes = assetDataSize,
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
                    sha256 = manipulatedSha256KeyData,
                    assetId = assetId.key,
                    encryptionAlgorithm = if (brokenState.otherAlgorithm) MessageEncryptionAlgorithm.AES_GCM else MessageEncryptionAlgorithm.AES_CBC,
                    assetDomain = assetId.domain,
                    assetToken = assetId.assetToken
                ),
                // Asset is already in our local storage and therefore accessible but until we don't save it to external storage the asset
                // will only be treated as "SAVED_INTERNALLY"
                downloadStatus = Message.DownloadStatus.SAVED_INTERNALLY,
                uploadStatus = uploadStatus
            )
        }
    }

    private companion object {
        const val DEFAULT_BYTE_ARRAY_SIZE = 16
    }
}

sealed class SendBrokenAssetMessageResult {
    object Success : SendBrokenAssetMessageResult()
    class Failure(val coreFailure: CoreFailure) : SendBrokenAssetMessageResult()
}

private data class AssetMessageMetadata(
    val conversationId: ConversationId,
    val mimeType: String,
    val assetId: UploadedAssetId,
    val assetDataPath: Path,
    val assetDataSize: Long,
    val assetName: String,
    val assetWidth: Int?,
    val assetHeight: Int?,
    val otrKey: AES256Key,
    val sha256Key: SHA256Key
)

data class BrokenState(
    val invalidHash: Boolean,
    val otherHash: Boolean,
    val otherAlgorithm: Boolean
)
