/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.asset

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.asset.isAudioMimeType
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
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import kotlin.time.Duration

interface ScheduleNewAssetMessageUseCase {
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
        audioLengthInMs: Long
    ): ScheduleNewAssetMessageResult
}

// TODO: https://github.com/wireapp/kalium/pull/1727, see Vitor comment
@Suppress("LongParameterList")
internal class ScheduleNewAssetMessageUseCaseImpl(
    private val persistMessage: PersistMessageUseCase,
    private val updateAssetMessageUploadStatus: UpdateAssetMessageUploadStatusUseCase,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val assetDataSource: AssetRepository,
    private val userId: UserId,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val messageRepository: MessageRepository,
    private val userPropertyRepository: UserPropertyRepository,
    private val selfDeleteTimer: ObserveSelfDeletionTimerSettingsForConversationUseCase,
    private val scope: CoroutineScope,
    private val observeFileSharingStatus: ObserveFileSharingStatusUseCase,
    private val validateAssetFileUseCase: ValidateAssetFileTypeUseCase,
    private val dispatcher: KaliumDispatcher,
) : ScheduleNewAssetMessageUseCase {

    private var outGoingAssetUploadJob: Job? = null

    @Suppress("LongMethod", "ReturnCount")
    override suspend fun invoke(
        conversationId: ConversationId,
        assetDataPath: Path,
        assetDataSize: Long,
        assetName: String,
        assetMimeType: String,
        assetWidth: Int?,
        assetHeight: Int?,
        audioLengthInMs: Long
    ): ScheduleNewAssetMessageResult {
        observeFileSharingStatus().first().also {
            when (it.state) {
                FileSharingStatus.Value.Disabled -> return ScheduleNewAssetMessageResult.Failure.DisabledByTeam
                FileSharingStatus.Value.EnabledAll -> { /* no-op*/
                }

                is FileSharingStatus.Value.EnabledSome -> if (!validateAssetFileUseCase(
                        fileName = assetName,
                        mimeType = assetMimeType,
                        allowedExtension = it.state.allowedType
                    )
                ) {
                    kaliumLogger.e("The asset message trying to be processed has invalid content data")
                    return ScheduleNewAssetMessageResult.Failure.RestrictedFileType
                }
            }
        }

        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }
        // Create a unique message ID
        val generatedMessageUuid = uuid4().toString()
        val expectsReadConfirmation = userPropertyRepository.getReadReceiptsStatus()

        val messageTimer = selfDeleteTimer(conversationId, true)
            .first()
            .duration

        return withContext(dispatcher.io) {
            // We persist the asset with temporary id and message right away so that it can be displayed on the conversation screen loading
            persistInitiallyAssetAndMessage(
                messageId = generatedMessageUuid,
                conversationId = conversationId,
                assetDataPath = assetDataPath,
                assetDataSize = assetDataSize,
                assetName = assetName,
                assetMimeType = assetMimeType,
                assetWidth = assetWidth,
                assetHeight = assetHeight,
                expireAfter = messageTimer,
                audioLengthInMs = audioLengthInMs,
                expectsReadConfirmation = expectsReadConfirmation
            ).onSuccess { (currentAssetMessageContent, message) ->
                // We schedule the asset upload and return Either.Right so later it's transformed to Success(message.id)
                outGoingAssetUploadJob = scope.launch(dispatcher.io) {
                    launch {
                        messageRepository.observeMessageVisibility(message.id, conversationId).collect { visibility ->
                            visibility.fold(
                                {
                                    outGoingAssetUploadJob?.cancel()
                                },
                                {
                                    if (it == MessageEntity.Visibility.DELETED) {
                                        outGoingAssetUploadJob?.cancel()
                                    }
                                }
                            )
                        }
                    }
                    launch {
                        uploadAssetAndUpdateMessage(currentAssetMessageContent, message, conversationId, expectsReadConfirmation)
                            .onSuccess {
                                // We delete asset added temporarily that was used to show the loading
                                assetDataSource.deleteAssetLocally(currentAssetMessageContent.assetId.key)
                            }
                    }
                }
            }
        }.fold({
            ScheduleNewAssetMessageResult.Failure.Generic(it)
        }, { (_, message) ->
            ScheduleNewAssetMessageResult.Success(message.id)
        })
    }

    private suspend fun persistInitiallyAssetAndMessage(
        messageId: String,
        conversationId: ConversationId,
        assetDataPath: Path,
        assetDataSize: Long,
        assetName: String,
        assetMimeType: String,
        assetWidth: Int?,
        assetHeight: Int?,
        expireAfter: Duration?,
        audioLengthInMs: Long,
        expectsReadConfirmation: Boolean
    ): Either<CoreFailure, Pair<AssetMessageMetadata, Message.Regular>> = currentClientIdProvider().flatMap { currentClientId ->
        // Create a temporary asset key and domain
        val (generatedAssetUuid, tempAssetDomain) = uuid4().toString() to ""
        withContext(dispatcher.io) {
            assetDataSource.persistAsset(generatedAssetUuid, tempAssetDomain, assetDataPath, assetDataSize, assetName.fileExtension())
                .flatMap { persistedAssetDataPath ->
                    // Generate the otr asymmetric key that will be used to encrypt the data
                    val otrKey = generateRandomAES256Key()
                    val currentAssetMessageContent = AssetMessageMetadata(
                        conversationId = conversationId,
                        mimeType = assetMimeType,
                        assetDataPath = persistedAssetDataPath,
                        assetDataSize = assetDataSize,
                        assetName = assetName,
                        assetWidth = assetWidth,
                        assetHeight = assetHeight,
                        otrKey = otrKey,
                        // Sha256 will be replaced with right values after asset upload
                        sha256Key = SHA256Key(byteArrayOf()),
                        // Asset ID will be replaced with right value after asset upload
                        assetId = UploadedAssetId(generatedAssetUuid, tempAssetDomain),
                        audioLengthInMs = audioLengthInMs
                    )

                    val message = Message.Regular(
                        id = messageId,
                        content = MessageContent.Asset(
                            provideAssetMessageContent(
                                assetMessageMetadata = currentAssetMessageContent,
                                // We set UPLOAD_IN_PROGRESS when persisting the message for the first time
                                uploadStatus = Message.UploadStatus.UPLOAD_IN_PROGRESS
                            )
                        ),
                        conversationId = conversationId,
                        date = DateTimeUtil.currentIsoDateTimeString(),
                        senderUserId = userId,
                        senderClientId = currentClientId,
                        status = Message.Status.Pending,
                        editStatus = Message.EditStatus.NotEdited,
                        expectsReadConfirmation = expectsReadConfirmation,
                        expirationData = expireAfter?.let { Message.ExpirationData(it) },
                        isSelfMessage = true
                    )
                    // We persist the asset message right away so that it can be displayed on the conversation screen loading
                    persistMessage(message).map { currentAssetMessageContent to message }
                }
                .onFailure {
                    updateAssetMessageUploadStatus(Message.UploadStatus.FAILED_UPLOAD, conversationId, messageId)
                    messageSendFailureHandler.handleFailureAndUpdateMessageStatus(it, conversationId, messageId, TYPE)
                }
        }
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
            messageSendFailureHandler.handleFailureAndUpdateMessageStatus(it, conversationId, message.id, TYPE)
        }.flatMap { (assetId, sha256) ->
            // We update the message with the remote data (assetId & sha256 key) obtained by the successful asset upload and we persist
            // and update the message on the DB layer to display the changes on the Conversation screen
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
            persistMessage(updatedMessage)
                .onFailure {
                    kaliumLogger.e(
                        "There was an error when trying to persist the updated asset message with the information returned by the backend"
                    )
                }.onSuccess {
                    // Finally we try to send the Asset Message to the recipients of the given conversation
                    val finalMessage = Message.Regular(
                        id = updatedMessage.id,
                        content = updatedMessage.content,
                        conversationId = updatedMessage.conversationId,
                        date = updatedMessage.date,
                        senderUserId = updatedMessage.senderUserId,
                        senderClientId = updatedMessage.senderClientId,
                        status = updatedMessage.status,
                        editStatus = updatedMessage.editStatus,
                        expectsReadConfirmation = updatedMessage.expectsReadConfirmation,
                        expirationData = updatedMessage.expirationData,
                        isSelfMessage = updatedMessage.isSelfMessage
                    )
                    messageSender.sendMessage(finalMessage).onFailure {
                        messageSendFailureHandler.handleFailureAndUpdateMessageStatus(it, conversationId, message.id, TYPE)
                    }
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
                    isDisplayableImageMimeType(mimeType) && (assetHeight.isGreaterThan(0) && (assetWidth.isGreaterThan(0))) -> {
                        AssetContent.AssetMetadata.Image(assetWidth, assetHeight)
                    }

                    isAudioMimeType(mimeType) -> {
                        AssetContent.AssetMetadata.Audio(
                            durationMs = audioLengthInMs,
                            normalizedLoudness = null
                        )
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
        const val TYPE = "Asset"
    }
}

sealed interface ScheduleNewAssetMessageResult {
    data class Success(val messageId: String) : ScheduleNewAssetMessageResult
    sealed interface Failure : ScheduleNewAssetMessageResult {
        data class Generic(val coreFailure: CoreFailure) : Failure
        data object DisabledByTeam : Failure
        data object RestrictedFileType : Failure
    }
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
    val sha256Key: SHA256Key,
    val audioLengthInMs: Long
)
