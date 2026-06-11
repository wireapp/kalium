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
package com.wire.kalium.logic.feature.message.linkpreview

import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewAsset
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Opportunistically resolves remotely-backed link preview images into persistent local files.
 *
 * This is intentionally fire-and-forget. It is safe to invoke from receiver paths and UI paths.
 */
public interface LinkPreviewImagesResolver {
    public operator fun invoke(conversationId: ConversationId, messageId: String)
}

internal class LinkPreviewImagesResolverImpl(
    private val messageRepository: MessageRepository,
    private val assetRepository: AssetRepository,
    private val linkPreviewEnabled: Boolean,
    private val scope: CoroutineScope,
    private val dispatcher: KaliumDispatcher,
) : LinkPreviewImagesResolver {

    override fun invoke(conversationId: ConversationId, messageId: String) {
        if (!linkPreviewEnabled) return

        scope.launch(dispatcher.io) {
            messageRepository.getMessageById(conversationId, messageId).onSuccess { message ->
                resolveMessage(conversationId, messageId, message)
            }
        }
    }

    private suspend fun resolveMessage(
        conversationId: ConversationId,
        messageId: String,
        message: Message
    ) {
        val linkPreviews = (message as? Message.Regular)?.linkPreviews.orEmpty()
        linkPreviews.forEach { preview ->
            val image = preview.image ?: return@forEach
            if (image.assetDataPath != null) return@forEach
            if (!image.hasRemoteAssetData()) return@forEach

            assetRepository.fetchPrivateDecodedAsset(
                assetId = image.assetKey.orEmpty(),
                assetDomain = image.assetDomain,
                assetName = image.assetName ?: "link-preview-${preview.urlOffset}",
                mimeType = image.mimeType,
                assetToken = image.assetToken,
                encryptionKey = AES256Key(image.otrKey),
                assetSHA256Key = SHA256Key(image.sha256Key),
                downloadIfNeeded = true
            ).onSuccess { (decodedAssetPath, _) ->
                messageRepository.updateLinkPreviewImageLocalPath(
                    conversationId = conversationId,
                    messageId = messageId,
                    urlOffset = preview.urlOffset,
                    localPath = decodedAssetPath.toString()
                )
            }.onFailure {
                kaliumLogger.w(
                    message = "Failed to resolve link preview image for messageId=$messageId urlOffset=${preview.urlOffset}: $it"
                )
            }
        }
    }
}

private fun LinkPreviewAsset.hasRemoteAssetData(): Boolean =
    !assetKey.isNullOrBlank() && otrKey.isNotEmpty() && sha256Key.isNotEmpty()
