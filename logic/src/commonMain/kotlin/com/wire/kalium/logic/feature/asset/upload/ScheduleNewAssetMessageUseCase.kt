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
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageResult.Failure
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
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
    private val scope: CoroutineScope,
    private val dispatcher: KaliumDispatcher,
) : ScheduleNewAssetMessageUseCase {

    private var outGoingAssetUploadJob: Job? = null

    @Suppress("LongMethod", "ReturnCount")
    override suspend fun invoke(asset: AssetUploadParams): ScheduleNewAssetMessageResult {

        validateAsset(asset).onFailure { error -> return error }

        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        return withContext(dispatcher.io) {
            val messageId = Uuid.random().toString()
            // We persist the asset with temporary id and message right away so that it can be displayed on the conversation screen loading
            persistNewAssetMessage(messageId, userId, asset)
                .onSuccess { (currentAssetMessageContent, message) ->
                    // We schedule the asset upload and return Either.Right so later it's transformed to Success(message.id)
                    outGoingAssetUploadJob = scope.launch {
                        launch { monitorAndCancelUploadOnMessageVisibilityChange(message) }
                        launch { uploadAsset(message, currentAssetMessageContent) }.invokeOnCompletion { reason ->
                            if (reason is CancellationException) {
                                scope.launch(NonCancellable) {
                                    updateAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD, message.conversationId, message.id)
                                }
                            }
                        }
                    }
                }
                .onFailure {
                    updateAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD, asset.conversationId, messageId)
                    messageSendFailureHandler.handleFailureAndUpdateMessageStatus(it, asset.conversationId, messageId, TYPE)
                }
        }.fold({
            Failure.Generic(it)
        }, { (_, message) ->
            ScheduleNewAssetMessageResult.Success(message.id)
        })
    }

    private fun monitorAndCancelUploadOnMessageVisibilityChange(
        message: Message.Regular,
    ): suspend CoroutineScope.() -> Unit = {
        messageRepository.observeMessageVisibility(message.id, message.conversationId).collect { visibility ->
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

    private suspend fun validateAsset(asset: AssetUploadParams): Either<Failure, Unit> =
        observeFileSharingStatus().firstOrNull()?.let { fileSharingStatus ->
            when (fileSharingStatus.state) {
                FileSharingStatus.Value.EnabledAll -> Unit.right()
                FileSharingStatus.Value.Disabled -> Failure.DisabledByTeam.left()

                is FileSharingStatus.Value.EnabledSome ->
                    if (!validateAssetFileUseCase(
                            fileName = asset.assetName,
                            mimeType = asset.assetMimeType,
                            allowedExtension = fileSharingStatus.state.allowedType
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
