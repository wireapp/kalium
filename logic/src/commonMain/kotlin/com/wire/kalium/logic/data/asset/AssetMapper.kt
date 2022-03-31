package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.*
import com.wire.kalium.logic.data.message.AssetContent.RemoteData.EncryptionAlgorithm.AES_CBC
import com.wire.kalium.logic.data.message.AssetContent.RemoteData.EncryptionAlgorithm.AES_GCM
import com.wire.kalium.network.api.asset.AssetMetadataRequest
import com.wire.kalium.network.api.asset.AssetResponse
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.persistence.dao.asset.AssetEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.EncryptionAlgorithm
import kotlinx.datetime.Clock

interface AssetMapper {
    fun toMetadataApiModel(uploadAssetMetadata: UploadAssetData): AssetMetadataRequest
    fun fromApiUploadResponseToDomainModel(asset: AssetResponse): UploadedAssetId
    fun fromUploadedAssetToDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity
    fun fromUserAssetToDaoModel(assetKey: String, data: ByteArray): AssetEntity
    fun fromAssetEntityToAssetContent(assetContentEntity: MessageEntity.MessageEntityContent.AssetMessageContent): AssetContent
    fun fromProtoAssetMessageToAssetContent(protoAssetMessage: Asset): AssetContent
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
                name = assetName,
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
                    otrKey = assetOtrKey,
                    sha256 = assetSha256Key,
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

    override fun fromProtoAssetMessageToAssetContent(protoAssetMessage: Asset): AssetContent {
        with(protoAssetMessage) {
            return AssetContent(
                size = original?.size?.toInt() ?: 0,
                name = original?.name,
                mimeType = original?.mimeType ?: "*/*",
                metadata = when (val metadataType = original?.metaData) {
                    is Asset.Original.MetaData.Image -> Image(width = metadataType.value.width, height = metadataType.value.height)
                    null -> null
                    else -> null
                },
                remoteData = with((status as Asset.Status.Uploaded).value) {
                    AssetContent.RemoteData(
                        otrKey = otrKey.array,
                        sha256 = sha256.array,
                        assetId = assetId ?: "",
                        assetDomain = assetDomain,
                        assetToken = assetToken,
                        encryptionAlgorithm = when (encryption) {
                            EncryptionAlgorithm.AES_CBC -> AES_CBC
                            EncryptionAlgorithm.AES_GCM -> AES_GCM
                            else -> null
                        }
                    )
                }
            )
        }
    }
}
