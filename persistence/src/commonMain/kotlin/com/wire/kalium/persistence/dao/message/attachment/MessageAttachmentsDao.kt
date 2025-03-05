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
package com.wire.kalium.persistence.dao.message.attachment

import com.wire.kalium.persistence.MessageAttachmentsQueries

interface MessageAttachmentsDao {
    suspend fun getAssetPath(assetId: String): String?
    suspend fun setLocalPath(assetId: String, path: String)
    suspend fun setPreviewUrl(assetId: String, previewUrl: String)
    suspend fun setTransferStatus(assetId: String, status: String)
}

internal class MessageAttachmentsDaoImpl(
    private val queries: MessageAttachmentsQueries,
) : MessageAttachmentsDao {

    override suspend fun getAssetPath(assetId: String): String? =
        queries.getAssetPath(asset_id = assetId).executeAsOneOrNull()?.asset_path

    override suspend fun setLocalPath(assetId: String, path: String) {
        queries.setLocalPath(
            local_path = path,
            asset_id = assetId
        )
    }

    override suspend fun setPreviewUrl(assetId: String, previewUrl: String) {
        queries.setPreviewUrl(
            preview_url = previewUrl,
            asset_id = assetId
        )
    }

    override suspend fun setTransferStatus(assetId: String, status: String) {
        queries.setTransferStatus(
            asset_transfer_status = status,
            asset_id = assetId
        )
    }
}
