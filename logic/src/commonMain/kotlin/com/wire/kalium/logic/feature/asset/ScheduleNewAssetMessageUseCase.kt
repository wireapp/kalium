/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.asset

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.asset.isDisplayableImageMimeType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
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
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.logic.util.isGreaterThan
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.Path
import kotlin.time.Duration

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
        assetHeight: Int?,
        expireAfter: Duration?
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
    private val userPropertyRepository: UserPropertyRepository,
    private val scope: CoroutineScope,
    private val dispatcher: KaliumDispatcher,
) : ScheduleNewAssetMessageUseCase {
    override suspend fun invoke(
        conversationId: ConversationId,
        assetDataPath: Path,
        assetDataSize: Long,
        assetName: String,
        assetMimeType: String,
        assetWidth: Int?,
        assetHeight: Int?,
        expireAfter: Duration?
    ): ScheduleNewAssetMessageResult {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        // Generate the otr asymmetric key that will be used to encrypt the data
        val otrKey = generateRandomAES256Key()
        val currentAssetMessageContent = AssetMessageMetadata(
            conversationId = conversationId,
            mimeType = assetMimeType,
            assetDataPath = assetDataPath,
            assetDataSize = assetDataSize,
            assetName = assetName,
            assetWidth = assetWidth,
            assetHeight = assetHeight,
            otrKey = otrKey,
            sha256Key = SHA256Key(ByteArray(DEFAULT_BYTE_ARRAY_SIZE)), // Sha256 will be replaced with right values after asset upload
            assetId = UploadedAssetId("", ""), // Asset ID will be replaced with right value after asset upload
        )
        lateinit var message: Message.Regular

        return currentClientIdProvider().flatMap { currentClientId ->
            // Create a unique message ID
            val generatedMessageUuid = uuid4().toString()
            val expectsReadConfirmation = userPropertyRepository.getReadReceiptsStatus()

            message = Message.Regular(
                id = generatedMessageUuid,
                content = MessageContent.Asset(
                    provideAssetMessageContent(
                        currentAssetMessageContent,
                        Message.UploadStatus.UPLOAD_IN_PROGRESS // We set UPLOAD_IN_PROGRESS when persisting the message for the first time
                    )
                ),
                conversationId = conversationId,
                date = DateTimeUtil.currentIsoDateTimeString(),
                senderUserId = userId,
                senderClientId = currentClientId,
                status = Message.Status.PENDING,
                editStatus = Message.EditStatus.NotEdited,
                expectsReadConfirmation = expectsReadConfirmation,
                expirationData = expireAfter?.let { duration ->
                    // normalize the duration in case it's 0 to null, so that the message is not expirable in that case
                    if (duration == Duration.ZERO) null
                    else Message.ExpirationData(expireAfter, Message.ExpirationData.SelfDeletionStatus.NotStarted)
                },
                isSelfMessage = true
            )

            // We persist the asset message right away so that it can be displayed on the conversation screen loading
            persistMessage(message).onSuccess {
                // We schedule the asset upload and return Either.Right(Unit) so later it's transformed to Success(message.id)
                scope.launch(dispatcher.io) {
                    uploadAssetAndUpdateMessage(currentAssetMessageContent, message, conversationId, expectsReadConfirmation)
                }
            }
        }.fold({
            updateAssetMessageUploadStatus(Message.UploadStatus.FAILED_UPLOAD, conversationId, message.id)
            ScheduleNewAssetMessageResult.Failure(it)
        }, {
            ScheduleNewAssetMessageResult.Success(message.id)
        })
    }

    private suspend fun uploadAssetAndUpdateMessage(
        currentAssetMessageContent: AssetMessageMetadata,
        message: Message.Regular,
        conversationId: ConversationId,
        expectsReadConfirmation: Boolean
    ): Either<CoreFailure, Unit> =
        // The assetDataSource will encrypt the data with the provided otrKey and upload it if successful
        assetDataSource.uploadAndPersistPrivateAsset(
            currentAssetMessageContent.mimeType,
            currentAssetMessageContent.assetDataPath,
            currentAssetMessageContent.otrKey,
            currentAssetMessageContent.assetName.fileExtension()
        ).onFailure {
            updateAssetMessageUploadStatus(Message.UploadStatus.FAILED_UPLOAD, conversationId, message.id)
        }.flatMap { (assetId, sha256) ->
            // We update the message with the remote data (assetId & sha256 key) obtained by the successful asset upload and we persist and
            // update the message on the DB layer to display the changes on the Conversation screen
            val updatedAssetMessageContent = currentAssetMessageContent.copy(sha256Key = sha256, assetId = assetId)
            val updatedMessage = message.copy(
                // We update the upload status to UPLOADED as the upload succeeded
                content = MessageContent.Asset(
                    value = provideAssetMessageContent(
                        updatedAssetMessageContent,
                        Message.UploadStatus.UPLOADED
                    )
                ),
                expectsReadConfirmation = expectsReadConfirmation
            )
            persistMessage(updatedMessage).onFailure {
                // TODO: Should we fail the whole message sending if the updated message persistence fails? Check when implementing AR-2408
                kaliumLogger.e(
                    "There was an error when trying to persist the updated asset message with the information returned by the backend "
                )
            }.onSuccess {
                // Finally we try to send the Asset Message to the recipients of the given conversation
                prepareAndSendAssetMessage(message, conversationId)
            }
        }

    @Suppress("LongParameterList")
    private suspend fun prepareAndSendAssetMessage(
        message: Message,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> =
        messageSender.sendPendingMessage(conversationId, message.id)

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
                    isDisplayableImageMimeType(mimeType) && (assetHeight.isGreaterThan(0) && (assetWidth.isGreaterThan(0))) -> {
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
