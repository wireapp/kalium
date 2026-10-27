/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.feature.giphy

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.giphy.DownloadedGif
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.asset.upload.AssetUploadParams
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageResult
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageUseCase
import com.wire.kalium.logic.feature.asset.upload.validateAssetSharing
import com.wire.kalium.logic.feature.message.MessageOperationResult
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * Sends a downloaded GIF the way web and the old iOS app do: an optional caption text message, then the GIF
 * as an `image/gif` asset. Receivers need nothing new. Both messages follow the conversation's self-deleting timer.
 *
 * [SendGifMessageResult.Success] means both messages are stored in the conversation. Like any message, they then
 * send in the background, and a failed delivery shows in the conversation with a retry.
 */
public interface SendGifMessageUseCase {
    /**
     * @param caption the text sent before the GIF, for example "via giphy.com", or null to send only the GIF
     * @param quotedMessageId the message the caption replies to; ignored without a caption
     */
    public suspend operator fun invoke(
        conversationId: ConversationId,
        downloadedGif: DownloadedGif,
        caption: String?,
        quotedMessageId: String?,
    ): SendGifMessageResult
}

internal class SendGifMessageUseCaseImpl(
    private val sendText: suspend (conversationId: ConversationId, text: String, quotedMessageId: String?) -> MessageOperationResult,
    private val sendAssetMessage: ScheduleNewAssetMessageUseCase,
    private val observeFileSharingStatus: ObserveFileSharingStatusUseCase,
    private val validateAssetFileType: ValidateAssetFileTypeUseCase,
    private val scope: CoroutineScope,
) : SendGifMessageUseCase {

    // The session scope owns the send: if the caller goes away while the caption is sending, the GIF still follows it.
    override suspend fun invoke(
        conversationId: ConversationId,
        downloadedGif: DownloadedGif,
        caption: String?,
        quotedMessageId: String?,
    ): SendGifMessageResult = scope.async {
        val assetName = "giphy-${downloadedGif.gif.id}.gif"
        // Checked first so a team setting never leaves a caption without its GIF.
        validateAssetSharing(observeFileSharingStatus, validateAssetFileType, assetName, GIF_MIME_TYPE).fold(
            { it.toSendGifMessageResult() },
            { sendCaptionAndGif(conversationId, downloadedGif, assetName, caption, quotedMessageId) }
        )
    }.await()

    private suspend fun sendCaptionAndGif(
        conversationId: ConversationId,
        downloadedGif: DownloadedGif,
        assetName: String,
        caption: String?,
        quotedMessageId: String?,
    ): SendGifMessageResult {
        val captionResult = caption?.let { sendText(conversationId, it, quotedMessageId) }
        // A caption that was stored but not delivered stays retryable in the conversation, so the GIF still follows it.
        return if (captionResult is MessageOperationResult.Failure && captionResult.messageId == null) {
            SendGifMessageResult.Failure(captionResult.error)
        } else {
            sendAssetMessage(
                AssetUploadParams(
                    conversationId = conversationId,
                    assetDataPath = downloadedGif.path,
                    assetDataSize = downloadedGif.sizeInBytes,
                    assetName = assetName,
                    assetMimeType = GIF_MIME_TYPE,
                    assetWidth = downloadedGif.gif.original.width,
                    assetHeight = downloadedGif.gif.original.height,
                    audioLengthInMs = 0L,
                    audioNormalizedLoudness = null,
                )
            ).toSendGifMessageResult()
        }
    }

    private fun ScheduleNewAssetMessageResult.toSendGifMessageResult(): SendGifMessageResult = when (this) {
        is ScheduleNewAssetMessageResult.Success -> SendGifMessageResult.Success
        ScheduleNewAssetMessageResult.Failure.DisabledByTeam -> SendGifMessageResult.DisabledByTeam
        ScheduleNewAssetMessageResult.Failure.RestrictedFileType -> SendGifMessageResult.RestrictedFileType
        is ScheduleNewAssetMessageResult.Failure.Generic -> SendGifMessageResult.Failure(coreFailure)
    }

    private companion object {
        const val GIF_MIME_TYPE = "image/gif"
    }
}
