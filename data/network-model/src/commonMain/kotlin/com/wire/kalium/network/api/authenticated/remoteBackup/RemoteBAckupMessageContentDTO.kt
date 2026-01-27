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
package com.wire.kalium.network.api.authenticated.remoteBackup

import com.wire.kalium.network.api.model.QualifiedID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed class representing different types of message content for sync.
 * This mirrors the structure of BackupMessageContent.
 */
@Serializable
sealed class RemoteBAckupMessageContentDTO {

    @Serializable
    @SerialName("text")
    data class Text(
        @SerialName("text")
        val text: String,
        @SerialName("mentions")
        val mentions: List<MessageSyncMentionDTO> = emptyList(),
        @SerialName("quotedMessageId")
        val quotedMessageId: String? = null
    ) : RemoteBAckupMessageContentDTO()

    @Serializable
    @SerialName("asset")
    data class Asset(
        @SerialName("mimeType")
        val mimeType: String,
        @SerialName("size")
        val size: Int,
        @SerialName("name")
        val name: String?,
        @SerialName("otrKey")
        val otrKey: String,
        @SerialName("sha256")
        val sha256: String,
        @SerialName("assetId")
        val assetId: String,
        @SerialName("assetToken")
        val assetToken: String?,
        @SerialName("assetDomain")
        val assetDomain: String?,
        @SerialName("encryption")
        val encryption: String?,
        @SerialName("metaData")
        val metaData: MessageSyncAssetMetadataDTO?
    ) : RemoteBAckupMessageContentDTO()

    @Serializable
    @SerialName("location")
    data class Location(
        @SerialName("longitude")
        val longitude: Float,
        @SerialName("latitude")
        val latitude: Float,
        @SerialName("name")
        val name: String?,
        @SerialName("zoom")
        val zoom: Int?
    ) : RemoteBAckupMessageContentDTO()
}

/**
 * DTO for user mentions in text messages.
 */
@Serializable
data class MessageSyncMentionDTO(
    @SerialName("userId")
    val userId: QualifiedID,
    @SerialName("start")
    val start: Int,
    @SerialName("length")
    val length: Int
)

/**
 * Sealed class representing different types of asset metadata.
 */
@Serializable
sealed class MessageSyncAssetMetadataDTO {

    @Serializable
    @SerialName("image")
    data class Image(
        @SerialName("width")
        val width: Int,
        @SerialName("height")
        val height: Int,
        @SerialName("tag")
        val tag: String?
    ) : MessageSyncAssetMetadataDTO()

    @Serializable
    @SerialName("video")
    data class Video(
        @SerialName("width")
        val width: Int?,
        @SerialName("height")
        val height: Int?,
        @SerialName("duration")
        val duration: Long?
    ) : MessageSyncAssetMetadataDTO()

    @Serializable
    @SerialName("audio")
    data class Audio(
        @SerialName("normalization")
        val normalization: String?,
        @SerialName("duration")
        val duration: Long?
    ) : MessageSyncAssetMetadataDTO()

    @Serializable
    @SerialName("generic")
    data class Generic(
        @SerialName("name")
        val name: String?
    ) : MessageSyncAssetMetadataDTO()
}
