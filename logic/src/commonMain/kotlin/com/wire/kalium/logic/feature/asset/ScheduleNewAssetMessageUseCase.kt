package com.wire.kalium.logic.feature.asset

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.asset.isDisplayableImageMimeType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.logic.util.isGreaterThan
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okio.Path

fun interface ScheduleNewAssetMessageUseCase {
    /**
     * Function that enables sending an asset message to a given conversation with the strategy of fire & forget. This message is persisted
     * locally and the asset upload is scheduled but not awaited, so returning a [ScheduleNewAssetMessageResult.Success] doesn't mean that
     * the asset upload succeeded, but instead that the creation and persistence of the initial asset message succeeded.
     *
     * @param conversationId the id of the conversation where the asset wants to be sent
     * @param assetDataPath the raw data of the asset to be uploaded to the backend and sent to the given conversation
     * @param assetDataSize the size of the original asset file
     * @param assetName the name of the original asset file
     * @param assetMimeType the type of the asset file
     * @return an [ScheduleNewAssetMessageResult] containing a [CoreFailure] in case the creation and the local persistence of the original
     * asset message went wrong or the [ScheduleNewAssetMessageResult.Success.messageId] in case the creation of the preview asset message
     * succeeded. Note that this doesn't imply that the asset upload will succeed, it just confirms that the creation and persistence of the
     * initial worked out.
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
    ): ScheduleNewAssetMessageResult
}

@Suppress("LongParameterList")
internal class ScheduleNewAssetMessageUseCaseImpl(
    private val persistMessage: PersistMessageUseCase,
    private val updateAssetMessageUploadStatus: UpdateAssetMessageUploadStatusUseCase,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val assetDataSource: AssetRepository,
    private val userId: UserId,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val messageRepository: MessageRepository,
    private val scope: CoroutineScope,
    private val dispatcher: KaliumDispatcher
) : ScheduleNewAssetMessageUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        assetDataPath: Path,
        assetDataSize: Long,
        assetName: String,
        assetMimeType: String,
        assetWidth: Int?,
        assetHeight: Int?
    ): ScheduleNewAssetMessageResult {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        val generatedMessageId = uuid4().toString()

        return currentClientIdProvider().map { currentClientId ->
            val otrKey = generateOtrKey()

            val assetMessageMetaData = AssetMessageMetadata(
                conversationId = conversationId,
                mimeType = assetMimeType,
                assetDataPath = assetDataPath,
                assetDataSize = assetDataSize,
                assetName = assetName,
                assetWidth = assetWidth,
                assetHeight = assetHeight,
                otrKey = otrKey,
                // Sha256 will be replaced with right values after asset upload
                sha256Key = SHA256Key(ByteArray(DEFAULT_BYTE_ARRAY_SIZE)),
                // Asset ID will be replaced with right value after asset upload
                assetId = UploadedAssetId("", ""),
            )

            val message = Message.Regular(
                id = generatedMessageId,
                content = MessageContent.Asset(
                    provideAssetMessageContent(
                        assetMessageMetaData,
                        // We set UPLOAD_IN_PROGRESS when persisting the message for the first time
                        Message.UploadStatus.UPLOAD_IN_PROGRESS
                    )
                ),
                conversationId = conversationId,
                date = DateTimeUtil.currentIsoDateTimeString(),
                senderUserId = userId,
                senderClientId = currentClientId,
                status = Message.Status.PENDING,
                editStatus = Message.EditStatus.NotEdited,
            )

            message to assetMessageMetaData
        }.flatMap { (message, assetMessageMetaData) ->
            // We persist the asset message right away so that it can be displayed on the conversation screen loading
            persistMessage(message).onSuccess {
                // We update the asset message upload status to UPLOAD_IN_PROGRESS so that we can track the progress of the asset upload
                uploadAssetAndUpdateMessage(
                    message,
                    conversationId,
                    assetMessageMetaData
                )
            }
        }.fold({ coreFailure ->
            updateAssetMessageUploadStatus(Message.UploadStatus.FAILED_UPLOAD, conversationId, generatedMessageId)
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SEARCH)
                .d("Failed to schedule asset message: $coreFailure")
            ScheduleNewAssetMessageResult.Failure(coreFailure)
        }, {
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SEARCH).d(
                "Asset message scheduled for upload"
            )
            ScheduleNewAssetMessageResult.Success(generatedMessageId)
        })
    }

    // Generate the otr asymmetric key that will be used to encrypt the data
    private fun generateOtrKey(): AES256Key {
        return generateRandomAES256Key()
    }

    val test = CoroutineExceptionHandler() { _, throwable ->
        kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SEARCH)
            .e("Error while uploading asset", throwable)
    }

    private suspend fun uploadAssetAndUpdateMessage(
        message: Message.Regular,
        conversationId: ConversationId,
        assetMessageMetaData: AssetMessageMetadata
    ): Either<CoreFailure, Unit> =
        try {
            val parentJob = Job()

            supervisorScope {
                withContext(scope.coroutineContext + parentJob) {
                    launch {
                        messageRepository.observeMessageVisibility(message.id, conversationId)
                            .collect { messageVisibility ->
                                if (messageVisibility == MessageEntity.Visibility.DELETED) {
                                    if (parentJob.isActive) {
                                        parentJob.cancel()
                                    }
                                }
                            }
                    }

                    kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SEARCH)
                        .d("Uploading asset with id: ${assetMessageMetaData.assetId}")

                    assetDataSource.uploadAndPersistPrivateAsset(
                        assetMessageMetaData.mimeType,
                        assetMessageMetaData.assetDataPath,
                        assetMessageMetaData.otrKey,
                        assetMessageMetaData.assetName.fileExtension()
                    ).onFailure {
                        kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SEARCH)
                            .d("Failed to upload asset with id: ${assetMessageMetaData.assetId}")
                        updateAssetMessageUploadStatus(Message.UploadStatus.FAILED_UPLOAD, conversationId, message.id)
                    }.flatMap { (assetId, sha256) ->
                        kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SEARCH)
                            .d("Successfully uploaded asset with id: $assetId")
                        // We update the message with the remote data (assetId & sha256 key) obtained by the successful asset upload
                        // and persist and update the message on the DB layer to display the changes on the Conversation screen
                        val updatedMessage = message.copy(
                            // We update the upload status to UPLOADED as the upload succeeded
                            content = MessageContent.Asset(
                                provideAssetMessageContent(
                                    assetMessageMetaData.copy(sha256Key = sha256, assetId = assetId),
                                    Message.UploadStatus.UPLOADED
                                )
                            )
                        )
                        kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SEARCH)
                            .d("Updating asset message with id: ${updatedMessage.id} with assetId: $assetId")
                        persistMessage(updatedMessage).onFailure {
                            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SEARCH)
                                .d("Failed to persist message with id: ${updatedMessage.id}")
                            // TODO: Should we fail the whole message sending if the updated message persistence fails? Check when implementing AR-2408
                            kaliumLogger.e(
                                "There was an error when trying to persist the updated asset message with the information returned by the backend "
                            )
                        }.onSuccess {
                            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SEARCH)
                                .d("Successfully persisted message with id: ${updatedMessage.id}")
                            // Finally we try to send the Asset Message to the recipients of the given conversation
                            prepareAndSendAssetMessage(message, conversationId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            print("Exception thrown")
            if (e is CancellationException) {
                // It is okay if we cancel anything here, we do not need to inform the user or react to it in any way
                Either.Right(Unit)
            } else {
                Either.Left(CoreFailure.Unknown(e))
            }
        }

    @Suppress("LongParameterList")
    private suspend fun prepareAndSendAssetMessage(
        message: Message.Regular,
        conversationId: ConversationId
    ): Either<CoreFailure, Message.Regular> {
        return messageSender.sendPendingMessage(conversationId, message.id).onFailure {
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SEARCH).d(
                "Failed to send message with id: ${message.id}"
            )
            kaliumLogger.e("There was an error when trying to send the asset on the conversation")
        }.flatMap {
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SEARCH).d(
                "Successfully sent message with id: ${message.id}"
            )
            Either.Right(message)
        }
    }

    @Suppress("LongParameterList")
    private fun provideAssetMessageContent(
        assetMessageMetadata: AssetMessageMetadata,
        uploadStatus: Message.UploadStatus
    ): AssetContent {
        with(assetMessageMetadata) {
            return AssetContent(
                sizeInBytes = assetDataSize,
                name = assetName,
                mimeType = mimeType,
                metadata = when {
                    isDisplayableImageMimeType(mimeType) && (
                            assetHeight.isGreaterThan(0) && (
                                    assetWidth.isGreaterThan(
                                        0
                                    )
                                    )
                            ) -> {
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

    private companion object {
        const val DEFAULT_BYTE_ARRAY_SIZE = 16
    }

}

sealed class ScheduleNewAssetMessageResult {
    class Success(val messageId: String) : ScheduleNewAssetMessageResult()
    class Failure(val coreFailure: CoreFailure) : ScheduleNewAssetMessageResult()
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
