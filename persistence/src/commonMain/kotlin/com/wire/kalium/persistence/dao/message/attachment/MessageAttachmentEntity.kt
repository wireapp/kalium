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

import com.wire.kalium.persistence.dao.message.BooleanIntSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageAttachmentEntity(
    @SerialName("id") val assetId: String,
    @SerialName("version_id") val assetVersionId: String = "",
    @Serializable(with = BooleanIntSerializer::class)
    @SerialName("cell_asset") val cellAsset: Boolean,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("asset_path") val assetPath: String?,
    @SerialName("asset_size") val assetSize: Long?,
    @SerialName("local_path") val localPath: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("asset_width") val assetWidth: Int?,
    @SerialName("asset_height") val assetHeight: Int?,
    @SerialName("asset_duration_ms") val assetDuration: Long?,
    @SerialName("asset_transfer_status") val assetTransferStatus: String,
    @SerialName("content_url") val contentUrl: String? = null,
    @SerialName("content_hash") val contentHash: String? = null,
)
