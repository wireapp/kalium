package com.wire.kalium.logic.feature.asset

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
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.logic.util.isGreaterThan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import okio.IOException
import okio.Path

fun interface SendAssetMessageUseCase {
    /**
     * Function that enables sending an asset message to a given conversation
     *
     * @param conversationId the id of the conversation where the asset wants to be sent
     * @param assetDataPath the raw data of the asset to be uploaded to the backend and sent to the given conversation
     * @param assetDataSize the size of the original asset file
     * @param assetName the name of the original asset file
     * @param assetMimeType the type of the asset file
     * @return an [SendAssetMessageResult] containing a [CoreFailure] in case anything goes wrong and [Unit] in case everything succeeds
     */
    @Suppress("LongParameterList")
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
    private val persistMessage: PersistMessageUseCase,
    private val updateAssetMessageUploadStatus: UpdateAssetMessageUploadStatusUseCase,
    private val clientRepository: ClientRepository,
    private val assetDataSource: AssetRepository,
    private val userRepository: UserRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender
) : SendAssetMessageUseCase {
    private lateinit var currentAssetMessageContent: AssetMessageMetadata
    override suspend fun invoke(
        conversationId: ConversationId,
        assetDataPath: Path,
        assetDataSize: Long,
        assetName: String,
        assetMimeType: String,
        assetWidth: Int?,
        assetHeight: Int?
    ): SendAssetMessageResult {
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
            assetWidth = assetWidth,
            assetHeight = assetHeight,
            otrKey = otrKey,
            sha256Key = SHA256Key(ByteArray(16)), // Sha256 will be replaced with right values after successful asset upload
            assetId = UploadedAssetId("", ""), // Asset ID will be replaced with right value after successful asset upload
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
                        Message.UploadStatus.UPLOAD_IN_PROGRESS // We set UPLOAD_IN_PROGRESS when persisting the message for the first time
                    )
                ),
                conversationId = conversationId,
                date = Clock.System.now().toString(),
                senderUserId = selfUser.id,
                senderClientId = currentClientId,
                status = Message.Status.PENDING,
                editStatus = Message.EditStatus.NotEdited
            )

            // We persist the asset message right away so that it can be displayed on the conversation screen loading
            persistMessage(message).flatMap {
                Either.Right(Unit)
            }.onFailure {
                kaliumLogger.e("Asset persist method failed")
                updateAssetMessageUploadStatus(Message.UploadStatus.FAILED_UPLOAD, conversationId, message.id)
                Either.Left(it)
            }.flatMap {
                uploadAssetAndUpdateMessage(message, conversationId)
            }
        }.fold({
            SendAssetMessageResult.Failure(it)
        }, { SendAssetMessageResult.Success })
    }

    private suspend fun uploadAssetAndUpdateMessage(message: Message.Regular, conversationId: ConversationId): Either<CoreFailure, Unit> =
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
                content = MessageContent.Asset(provideAssetMessageContent(currentAssetMessageContent, Message.UploadStatus.UPLOADED))
            )
            persistMessage(updatedMessage).onFailure {
                // TODO: Should we fail the whole message sending if the updated message persistance fails? Check when implementing AR-2408
                kaliumLogger.e(
                    "There was an error when trying to persist the updated asset message with the information returned by the backend "
                )
            }.onSuccess {
                // Finally we try to send the Asset Message to the recipients of the given conversation
                prepareAndSendAssetMessage(message, conversationId)
            }
        }.flatMap {
            Either.Right(Unit)
        }.onFailure {
            // TODO: Should we update the upload status as FAILED_UPLOAD even if the upload succeeded? Decide when implementing AR-2408
            updateAssetMessageUploadStatus(Message.UploadStatus.FAILED_UPLOAD, conversationId, message.id)
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

    @Suppress("LongParameterList")
    private fun provideAssetMessageContent(assetMessageMetadata: AssetMessageMetadata, uploadStatus: Message.UploadStatus): AssetContent {
        with(assetMessageMetadata) {
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
                    sha256 = sha256Key.data,
                    assetId = assetId.key,
                    encryptionAlgorithm = MessageEncryptionAlgorithm.AES_CBC,
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
}

sealed class SendAssetMessageResult {
    object Success : SendAssetMessageResult()
    class Failure(val coreFailure: CoreFailure) : SendAssetMessageResult()
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
