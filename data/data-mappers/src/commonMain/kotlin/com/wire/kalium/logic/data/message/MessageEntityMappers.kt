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
package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.persistence.dao.message.DeliveryStatusEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent

public fun MessageEntityContent.Asset.toAssetContent(): AssetContent = AssetContent(
    sizeInBytes = assetSizeInBytes,
    name = assetName,
    mimeType = assetMimeType,
    metadata = when {
        assetMimeType.contains("image/") -> Image(
            width = assetWidth ?: 0,
            height = assetHeight ?: 0,
        )

        assetMimeType.contains("video/") -> Video(
            width = assetWidth,
            height = assetHeight,
            durationMs = assetDurationMs,
        )

        assetMimeType.contains("audio/") -> Audio(
            durationMs = assetDurationMs,
            normalizedLoudness = assetNormalizedLoudness,
        )

        else -> null
    },
    remoteData = AssetContent.RemoteData(
        otrKey = assetOtrKey,
        sha256 = assetSha256Key,
        assetId = assetId,
        assetToken = assetToken,
        assetDomain = assetDomain,
        encryptionAlgorithm = when {
            assetEncryptionAlgorithm?.contains("CBC") == true -> MessageEncryptionAlgorithm.AES_CBC
            assetEncryptionAlgorithm?.contains("GCM") == true -> MessageEncryptionAlgorithm.AES_GCM
            else -> MessageEncryptionAlgorithm.AES_CBC
        },
    ),
)

public fun MessageEntity.Status.toModel(readCount: Long): Message.Status =
    when (this) {
        MessageEntity.Status.PENDING -> Message.Status.Pending
        MessageEntity.Status.SENT -> Message.Status.Sent
        MessageEntity.Status.DELIVERED -> Message.Status.Delivered
        MessageEntity.Status.READ -> Message.Status.Read(readCount)
        MessageEntity.Status.FAILED -> Message.Status.Failed
        MessageEntity.Status.FAILED_REMOTELY -> Message.Status.FailedRemotely
    }

public fun MessageEntity.Visibility.toModel(): Message.Visibility = when (this) {
    MessageEntity.Visibility.VISIBLE -> Message.Visibility.VISIBLE
    MessageEntity.Visibility.HIDDEN -> Message.Visibility.HIDDEN
    MessageEntity.Visibility.DELETED -> Message.Visibility.DELETED
}

@OptIn(InternalKaliumApi::class)
public fun DeliveryStatusEntity.toModel(): DeliveryStatus = when (this) {
    DeliveryStatusEntity.CompleteDelivery -> DeliveryStatus.CompleteDelivery
    is DeliveryStatusEntity.PartialDelivery -> DeliveryStatus.PartialDelivery(
        recipientsFailedWithNoClients = recipientsFailedWithNoClients.map { it.toModel() },
        recipientsFailedDelivery = recipientsFailedDelivery.map { it.toModel() },
    )
}
