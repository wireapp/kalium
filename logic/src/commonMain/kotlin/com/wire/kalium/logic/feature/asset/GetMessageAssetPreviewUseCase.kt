/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

public interface GetMessageAssetPreviewUseCase {
    /** Resolves only the optional image preview, without downloading or changing the transfer status of the original asset. */
    public suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String,
        expectedPreviewAssetId: AssetId? = null,
    ): Deferred<MessageAssetResult>
}

internal class GetMessageAssetPreviewUseCaseImpl(
    private val assetRepository: AssetRepository,
    private val messageRepository: MessageRepository,
    private val scope: CoroutineScope,
    private val dispatcher: KaliumDispatcher,
) : GetMessageAssetPreviewUseCase {
    @Suppress("LongMethod")
    override suspend fun invoke(
        conversationId: ConversationId,
        messageId: String,
        expectedPreviewAssetId: AssetId?,
    ): Deferred<MessageAssetResult> = scope.async(dispatcher.io) {
        messageRepository.getMessageById(conversationId, messageId).fold(
            { MessageAssetResult.Failure(it, false) },
            { message ->
                val preview = (message.content as? MessageContent.Asset)?.value?.preview
                if (preview == null || (
                        expectedPreviewAssetId != null &&
                            expectedPreviewAssetId != AssetId(preview.remoteData.assetId, preview.remoteData.assetDomain.orEmpty())
                        )
                ) {
                    MessageAssetResult.Failure(CoreFailure.Unknown(IllegalStateException("Asset preview unavailable")), false)
                } else {
                    val remote = preview.remoteData
                    // Pending previews have a managed local copy and no ciphertext hash yet.
                    val local = assetRepository.fetchDecodedAsset(remote.assetId).getOrNull()
                    if (local != null) {
                        MessageAssetResult.Success(local, preview.sizeInBytes, "preview.jpg")
                    } else if (remote.sha256.isEmpty()) {
                        MessageAssetResult.Failure(CoreFailure.Unknown(IllegalStateException("Local preview unavailable")), false)
                    } else {
                        assetRepository.fetchPrivateDecodedAsset(
                            assetId = remote.assetId,
                            assetDomain = remote.assetDomain,
                            assetName = "preview.jpg",
                            mimeType = preview.mimeType,
                            assetToken = remote.assetToken,
                            encryptionKey = AES256Key(remote.otrKey),
                            assetSHA256Key = SHA256Key(remote.sha256),
                            downloadIfNeeded = true,
                        ).fold(
                            { MessageAssetResult.Failure(it, true) },
                            { MessageAssetResult.Success(it.path, preview.sizeInBytes, "preview.jpg") },
                        )
                    }
                }
            },
        )
    }
}
