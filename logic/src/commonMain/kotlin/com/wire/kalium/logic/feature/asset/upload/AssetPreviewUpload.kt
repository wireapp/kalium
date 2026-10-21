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

package com.wire.kalium.logic.feature.asset.upload

import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.message.AssetContent

/** Optional previews fail open; cancellation still propagates from repository calls. */
@Suppress("ReturnCount")
internal suspend fun AssetRepository.uploadPreviewIfNeeded(
    preview: AssetContent.Preview?,
    conversationId: ConversationId,
): AssetContent.Preview? {
    preview ?: return null
    if (preview.sizeInBytes !in 1..AssetContent.Preview.MAX_SIZE_IN_BYTES) return null
    if (preview.remoteData.sha256.isNotEmpty()) return preview
    val remote = preview.remoteData
    return fetchPreviewAsset(preview).flatMap { local ->
        uploadAndPersistPrivateAsset(
            mimeType = preview.mimeType,
            assetDataPath = local.path,
            otrKey = AES256Key(remote.otrKey),
            extension = "jpg",
            conversationId = conversationId.toApi(),
            filename = "preview.jpg",
            filetype = preview.mimeType,
        )
    }.getOrNull()?.let { (id, sha) ->
        preview.copy(remoteData = remote.copy(assetId = id.key, assetDomain = id.domain, assetToken = id.assetToken, sha256 = sha.data))
    }
}

internal suspend fun AssetRepository.deleteTemporaryPreview(previous: AssetContent.Preview?, current: AssetContent.Preview?) {
    if (previous != null && previous.remoteData.assetId != current?.remoteData?.assetId) {
        deleteAssetLocally(previous.remoteData.assetId)
    }
}
