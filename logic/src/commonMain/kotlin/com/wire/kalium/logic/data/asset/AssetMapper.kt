package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.logic.data.message.AssetContent.RemoteData.EncryptionAlgorithm.AES_CBC
import com.wire.kalium.logic.data.message.AssetContent.RemoteData.EncryptionAlgorithm.AES_GCM
import com.wire.kalium.network.api.asset.AssetMetadataRequest
import com.wire.kalium.network.api.asset.AssetResponse
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.persistence.dao.asset.AssetEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.datetime.Clock

interface AssetMapper {
    fun toMetadataApiModel(uploadAssetMetadata: UploadAssetData): AssetMetadataRequest
    fun fromApiUploadResponseToDomainModel(asset: AssetResponse): UploadedAssetId
    fun fromUploadedAssetToDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity
    fun fromUserAssetToDaoModel(assetKey: String, data: ByteArray): AssetEntity
    fun fromAssetEntityToAssetContent(assetContentEntity: MessageEntity.MessageEntityContent.AssetMessageContent): AssetContent
}

class AssetMapperImpl : AssetMapper {
    override fun toMetadataApiModel(uploadAssetMetadata: UploadAssetData): AssetMetadataRequest {
        return AssetMetadataRequest(
            uploadAssetMetadata.mimeType.name,
            uploadAssetMetadata.isPublic,
            AssetRetentionType.valueOf(uploadAssetMetadata.retentionType.name),
            calcMd5(uploadAssetMetadata.data)
        )
    }

    override fun fromApiUploadResponseToDomainModel(asset: AssetResponse) =
        UploadedAssetId(asset.key)

    override fun fromUploadedAssetToDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity {
        return AssetEntity(
            key = uploadedAssetResponse.key,
            domain = uploadedAssetResponse.domain,
            mimeType = uploadAssetData.mimeType.name,
            rawData = uploadAssetData.data,
            downloadedDate = Clock.System.now().toEpochMilliseconds()
        )
    }

    override fun fromUserAssetToDaoModel(assetKey: String, data: ByteArray): AssetEntity {
        return AssetEntity(
            key = assetKey,
            domain = "", // is it possible to know this on contacts sync avatars ?
            mimeType = ImageAsset.JPEG.name,
            rawData = data,
            downloadedDate = Clock.System.now().toEpochMilliseconds()
        )
    }

    override fun fromAssetEntityToAssetContent(assetContentEntity: MessageEntity.MessageEntityContent.AssetMessageContent): AssetContent {
        with(assetContentEntity) {
            return AssetContent(
                mimeType = assetMimeType,
                size = assetSize,
                name = assetName ?: "",
                metadata = when {
                    assetMimeType.contains("image/") -> Image(
                        width = assetImageWidth ?: 0,
                        height = assetImageHeight ?: 0
                    )
                    assetMimeType.contains("video/") -> Video(
                        width = assetVideoWidth,
                        height = assetVideoHeight,
                        durationMs = assetVideoDurationMs
                    )
                    assetMimeType.contains("audio/") -> Audio(
                        durationMs = assetAudioDurationMs,
                        normalizedLoudness = assetAudioNormalizedLoudness
                    )
                    else -> null
                },
                remoteData = AssetContent.RemoteData(
                    otrKey = assetOtrKey ?: ByteArray(16),
                    sha256 = assetSha256Key ?: ByteArray(16),
                    assetId = assetId,
                    assetToken = assetToken,
                    assetDomain = assetDomain,
                    encryptionAlgorithm = when {
                        assetEncryptionAlgorithm?.contains("CBC") == true -> AES_CBC
                        assetEncryptionAlgorithm?.contains("GCM") == true -> AES_GCM
                        else -> AES_CBC
                    }
                )
            )
        }
    }
}
