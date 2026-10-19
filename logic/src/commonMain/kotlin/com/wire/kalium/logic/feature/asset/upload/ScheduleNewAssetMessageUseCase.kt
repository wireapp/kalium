/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.asset.upload

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageResult.Failure
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okio.Path
import kotlin.uuid.Uuid

public interface ScheduleNewAssetMessageUseCase {
    /**
     * Function that enables sending an asset message to a given conversation with the strategy of fire & forget. This message is persisted
     * locally and the asset upload is scheduled but not awaited, so returning a [ScheduleNewAssetMessageResult.Success] doesn't mean that
     * the asset upload succeeded, but instead that the creation and persistence of the initial asset message succeeded.
     *
     * @param asset the parameters required to create and upload the new asset
     * @return an [ScheduleNewAssetMessageResult] containing a [CoreFailure] in case the creation and the local persistence of the original
     * asset message went wrong or the [ScheduleNewAssetMessageResult.Success.messageId] in case the creation of the preview asset message
     * succeeded. Note that this doesn't imply that the asset upload will succeed, it just confirms that the creation and persistence of the
     * initial worked out.
     */
    public suspend operator fun invoke(
        asset: AssetUploadParams
    ): ScheduleNewAssetMessageResult
}

@Suppress("LongParameterList")
internal class ScheduleNewAssetMessageUseCaseImpl(
    private val persistNewAssetMessage: PersistNewAssetMessageUseCase,
    private val uploadAsset: UploadAssetUseCase,
    private val updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
    private val userId: UserId,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageRepository: MessageRepository,
    private val observeFileSharingStatus: ObserveFileSharingStatusUseCase,
    private val validateAssetFileUseCase: ValidateAssetFileTypeUseCase,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val pendingMessagesEnabled: Boolean = true,
    private val scope: CoroutineScope,
    private val dispatcher: KaliumDispatcher,
) : ScheduleNewAssetMessageUseCase {

    override suspend fun invoke(asset: AssetUploadParams): ScheduleNewAssetMessageResult = invoke(asset, Uuid.random().toString())

    /** Schedules [asset] as [messageId], so a caller that may repeat itself can check for the message first (ADR 13). */
    internal suspend fun invoke(asset: AssetUploadParams, messageId: String): ScheduleNewAssetMessageResult =
        when (val result = startTracked(asset, messageId)) {
            is StartTrackedAssetUploadResult.Started -> ScheduleNewAssetMessageResult.Success(result.operation.messageId)
            is StartTrackedAssetUploadResult.Failure -> result.reason
        }

    internal suspend fun startTracked(asset: AssetUploadParams, messageId: String): StartTrackedAssetUploadResult {
        validateAsset(asset).onFailure { return StartTrackedAssetUploadResult.Failure(it) }
        slowSyncRepository.slowSyncStatus.first { it is SlowSyncStatus.Complete }

        var operation: TrackedAssetUpload? = null
        return try {
            withContext(dispatcher.io) {
                persistNewAssetMessage(messageId, userId, asset)
                    .onFailure {
                        updateAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD, asset.conversationId, messageId)
                        messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                            failure = it,
                            conversationId = asset.conversationId,
                            messageId = messageId,
                            messageType = TYPE,
                            scheduleResendIfNoNetwork = pendingMessagesEnabled
                        )
                    }.fold(
                        { StartTrackedAssetUploadResult.Failure(Failure.Generic(it)) },
                        { (metadata, message) ->
                            val started = createOperation(message, metadata, asset, isRetry = false)
                            operation = started
                            StartTrackedAssetUploadResult.Started(started)
                        }
                    )
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                val started = operation
                if (started != null) {
                    started.cancel()
                    started.awaitCompletion()
                } else {
                    // Persistence may have committed before cancellation prevented it from returning its result.
                    markCancelled(asset.conversationId, messageId)
                }
            }
            throw cancelled
        }
    }

    private fun createOperation(
        originalMessage: Message.Regular,
        metadata: UploadAssetMessageMetadata,
        asset: AssetUploadParams,
        isRetry: Boolean,
    ): TrackedAssetUpload = TrackedAssetUploadImpl(
        messageId = originalMessage.id,
        scope = scope,
        retryOperation = { createOperation(originalMessage, metadata, asset, isRetry = true) },
        monitor = {
            val deleted = messageRepository.observeMessageVisibility(originalMessage.id, originalMessage.conversationId)
                .firstOrNull { visibility -> visibility.fold({ true }, { it == MessageEntity.Visibility.DELETED }) }
            if (deleted == null) awaitCancellation()
        },
        upload = {
            withContext(dispatcher.io) {
                if (isRetry) {
                    validateAsset(asset).fold(
                        { failure ->
                            when (failure) {
                                is Failure.Generic -> failure.coreFailure
                                else -> CoreFailure.Unknown(IllegalStateException("Asset retry blocked by policy: $failure"))
                            }.left()
                        },
                        { Unit.right() }
                    ).flatMap { retryUpload(originalMessage, metadata) }
                } else {
                    uploadAsset(originalMessage, metadata)
                }
            }
        },
        onCancelled = {
            markCancelled(originalMessage.conversationId, originalMessage.id)
        }
    )

    private suspend fun markCancelled(conversationId: ConversationId, messageId: String) {
        val persisted = messageRepository.getMessageById(conversationId, messageId).getOrNull()
        if (persisted?.status == Message.Status.Sent) {
            currentCoroutineContext()[AssetMessageSendReceipt]?.succeeded = true
            return
        }
        val content = persisted?.content as? MessageContent.Asset
        val assetUploaded = content?.value?.remoteData?.sha256?.isNotEmpty() == true
        updateAssetMessageTransferStatus(
            if (assetUploaded) AssetTransferStatus.UPLOADED else AssetTransferStatus.FAILED_UPLOAD,
            conversationId,
            messageId
        )
        messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
            failure = CoreFailure.Unknown(CancellationException("Asset message sending was cancelled")),
            conversationId = conversationId,
            messageId = messageId,
            messageType = TYPE,
            scheduleResendIfNoNetwork = false
        )
    }

    private suspend fun retryUpload(
        originalMessage: Message.Regular,
        metadata: UploadAssetMessageMetadata,
    ): Either<CoreFailure, Unit> =
        // The upload may have succeeded even when message sending failed. Reuse its persisted remote data.
        messageRepository.getMessageById(originalMessage.conversationId, originalMessage.id).flatMap { persisted ->
            val message = persisted as? Message.Regular
            val content = message?.content as? MessageContent.Asset
            if (message == null || content == null) {
                CoreFailure.Unknown(IllegalStateException("Asset message is unavailable for retry")).left()
            } else if (message.status == Message.Status.Sent) {
                Unit.right()
            } else {
                val remoteData = content.value.remoteData
                uploadAsset(
                    message,
                    metadata.copy(
                        assetId = UploadedAssetId(remoteData.assetId, remoteData.assetDomain.orEmpty(), remoteData.assetToken),
                        otrKey = AES256Key(remoteData.otrKey),
                        sha256Key = SHA256Key(remoteData.sha256)
                    )
                )
            }
        }

    private suspend fun validateAsset(asset: AssetUploadParams): Either<Failure, Unit> =
        observeFileSharingStatus().firstOrNull()?.let { fileSharingStatus ->
            when (val state = fileSharingStatus.state) {
                FileSharingStatus.Value.EnabledAll -> Unit.right()
                FileSharingStatus.Value.Disabled -> Failure.DisabledByTeam.left()

                is FileSharingStatus.Value.EnabledSome ->
                    if (!validateAssetFileUseCase(
                            fileName = asset.assetName,
                            mimeType = asset.assetMimeType,
                            allowedExtension = state.allowedType
                        )
                    ) {
                        kaliumLogger.e("The asset message trying to be processed has invalid content data")
                        Failure.RestrictedFileType.left()
                    } else {
                        Unit.right()
                    }
            }
        } ?: Failure.Generic(CoreFailure.Unknown(null)).left()

    private companion object {
        const val TYPE = "Asset"
    }
}

public sealed interface ScheduleNewAssetMessageResult {
    public data class Success(val messageId: String) : ScheduleNewAssetMessageResult
    public sealed interface Failure : ScheduleNewAssetMessageResult {
        public data class Generic(val coreFailure: CoreFailure) : Failure
        public data object DisabledByTeam : Failure
        public data object RestrictedFileType : Failure
    }
}

public data class AssetUploadParams(
    val conversationId: ConversationId,
    val assetDataPath: Path,
    val assetDataSize: Long,
    val assetName: String,
    val assetMimeType: String,
    val assetWidth: Int?,
    val assetHeight: Int?,
    val audioLengthInMs: Long,
    val audioNormalizedLoudness: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as AssetUploadParams
        if (assetDataSize != other.assetDataSize) return false
        if (assetWidth != other.assetWidth) return false
        if (assetHeight != other.assetHeight) return false
        if (audioLengthInMs != other.audioLengthInMs) return false
        if (conversationId != other.conversationId) return false
        if (assetDataPath != other.assetDataPath) return false
        if (assetName != other.assetName) return false
        if (assetMimeType != other.assetMimeType) return false
        if (!audioNormalizedLoudness.contentEquals(other.audioNormalizedLoudness)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = assetDataSize.hashCode()
        result = 31 * result + (assetWidth ?: 0)
        result = 31 * result + (assetHeight ?: 0)
        result = 31 * result + audioLengthInMs.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + assetDataPath.hashCode()
        result = 31 * result + assetName.hashCode()
        result = 31 * result + assetMimeType.hashCode()
        result = 31 * result + (audioNormalizedLoudness?.contentHashCode() ?: 0)
        return result
    }
}
