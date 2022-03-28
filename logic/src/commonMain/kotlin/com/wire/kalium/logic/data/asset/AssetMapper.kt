package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.logic.data.message.AssetProtoContent
import com.wire.kalium.logic.data.message.AssetProtoContent.AssetMetadata.Image
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
    fun fromMessageEntityToAssetProtoContent(messageEntity: MessageEntity): AssetProtoContent
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
            mimeType = "",
            rawData = data,
            downloadedDate = Clock.System.now().toEpochMilliseconds()
        )
    }

    override fun fromMessageEntityToAssetProtoContent(messageEntity: MessageEntity): AssetProtoContent {
        // TODO: Complete mapping once all the necessary extra fields for the Asset Message have been added to the DB
        return AssetProtoContent(
            original = AssetProtoContent.Original(
                mimeType = "*/*", // TODO: add a mimeType to the MessageEntity table
                size = 0, // TODO: add a size field to the MessageEntity table
                name = "", // TODO: map the asset name from the exif info or the original file title?
                metadata = getAssetMessageMetadata(null),
                source = null, // TODO: add a source field to the MessageEntity DB table
                caption = null // TODO: add a caption field to the MessageEntity DB table
            ),
            preview = null,
            uploadStatus = null
        )
    }

    private fun getAssetMessageMetadata(mimeType: String?): AssetProtoContent.AssetMetadata {
       return when {
            mimeType?.contains("image/") ?: false -> Image(width = 0, height = 0, tag = null)
            // TODO: retrieve the image dimensions from the assetEntity object once these fields have been added to the DB table
            else -> Image(
                width = 0,
                height = 0,
                tag = null
            )
        }
    }
}
