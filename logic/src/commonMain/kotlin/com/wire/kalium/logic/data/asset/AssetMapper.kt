package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.logic.data.message.AssetContent.RemoteData.EncryptionAlgorithm.AES_CBC
import com.wire.kalium.logic.data.message.AssetContent.RemoteData.EncryptionAlgorithm.AES_GCM
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.network.api.AssetId
import com.wire.kalium.network.api.asset.AssetMetadataRequest
import com.wire.kalium.network.api.asset.AssetResponse
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.persistence.dao.asset.AssetEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.EncryptionAlgorithm
import kotlinx.datetime.Clock

interface AssetMapper {
    fun toMetadataApiModel(uploadAssetMetadata: UploadAssetData): AssetMetadataRequest
    fun fromApiUploadResponseToDomainModel(asset: AssetResponse): UploadedAssetId
    fun fromUploadedAssetToDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity
    fun fromUserAssetToDaoModel(assetId: AssetId, data: ByteArray): AssetEntity
    fun fromAssetEntityToAssetContent(assetContentEntity: MessageEntityContent.Asset): AssetContent
    fun fromProtoAssetMessageToAssetContent(protoAssetMessage: Asset): AssetContent
    fun fromDownloadStatusToDaoModel(downloadStatus: Message.DownloadStatus): MessageEntity.DownloadStatus
    fun fromDownloadStatusEntityToLogicModel(downloadStatus: MessageEntity.DownloadStatus?): Message.DownloadStatus
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
        UploadedAssetId(key = asset.key, domain = asset.domain, assetToken = asset.token)

    override fun fromUploadedAssetToDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity {
        return AssetEntity(
            key = uploadedAssetResponse.key,
            domain = uploadedAssetResponse.domain,
            mimeType = uploadAssetData.mimeType.name,
            rawData = uploadAssetData.data,
            downloadedDate = Clock.System.now().toEpochMilliseconds()
        )
    }

    override fun fromUserAssetToDaoModel(assetId: AssetId, data: ByteArray): AssetEntity {
        return AssetEntity(
            key = assetId.value,
            domain = assetId.domain,
            mimeType = ImageAsset.JPEG.name,
            rawData = data,
            downloadedDate = Clock.System.now().toEpochMilliseconds()
        )
    }

    override fun fromAssetEntityToAssetContent(assetContentEntity: MessageEntityContent.Asset): AssetContent {
        with(assetContentEntity) {
            return AssetContent(
                mimeType = assetMimeType,
                sizeInBytes = assetSizeInBytes,
                name = assetName,
                metadata = getAssetContentMetadata(assetMimeType, assetContentEntity),
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
                ),
                downloadStatus = fromDownloadStatusEntityToLogicModel(assetDownloadStatus)
            )
        }
    }

    private fun getAssetContentMetadata(
        assetMimeType: String,
        assetContentEntity: MessageEntityContent.Asset
    ): AssetContent.AssetMetadata? =
        with(assetContentEntity) {
            when {
                assetMimeType.contains("image/") -> Image(
                    width = assetWidth ?: 0,
                    height = assetHeight ?: 0
                )
                assetMimeType.contains("video/") -> Video(
                    width = assetWidth,
                    height = assetHeight,
                    durationMs = assetDurationMs
                )
                assetMimeType.contains("audio/") -> Audio(
                    durationMs = assetDurationMs,
                    normalizedLoudness = assetNormalizedLoudness
                )
                else -> null
            }
        }

    @Suppress("ComplexMethod")
    override fun fromProtoAssetMessageToAssetContent(protoAssetMessage: Asset): AssetContent {
        val defaultRemoteData = AssetContent.RemoteData(
            otrKey = ByteArray(0),
            sha256 = ByteArray(0),
            assetId = "",
            assetDomain = null,
            assetToken = null,
            encryptionAlgorithm = null
        )
        with(protoAssetMessage) {
            return AssetContent(
                sizeInBytes = original?.size ?: 0,
                name = original?.name,
                mimeType = original?.mimeType ?: "*/*",
                metadata = when (val metadataType = original?.metaData) {
                    is Asset.Original.MetaData.Image -> Image(width = metadataType.value.width, height = metadataType.value.height)
                    null -> null
                    else -> null
                },
                remoteData = status?.run {
                    when (this) {
                        is Asset.Status.Uploaded -> {
                            with(value) {
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
                        }
                        is Asset.Status.NotUploaded -> defaultRemoteData
                    }
                } ?: defaultRemoteData,
                downloadStatus = Message.DownloadStatus.NOT_DOWNLOADED
            )
        }
    }

    override fun fromDownloadStatusToDaoModel(downloadStatus: Message.DownloadStatus): MessageEntity.DownloadStatus {
        return when (downloadStatus) {
            Message.DownloadStatus.NOT_DOWNLOADED -> MessageEntity.DownloadStatus.NOT_DOWNLOADED
            Message.DownloadStatus.IN_PROGRESS -> MessageEntity.DownloadStatus.IN_PROGRESS
            Message.DownloadStatus.SAVED_INTERNALLY -> MessageEntity.DownloadStatus.SAVED_INTERNALLY
            Message.DownloadStatus.SAVED_EXTERNALLY -> MessageEntity.DownloadStatus.SAVED_EXTERNALLY
            Message.DownloadStatus.FAILED -> MessageEntity.DownloadStatus.FAILED
        }
    }

    override fun fromDownloadStatusEntityToLogicModel(downloadStatus: MessageEntity.DownloadStatus?): Message.DownloadStatus {
        return when (downloadStatus) {
            MessageEntity.DownloadStatus.NOT_DOWNLOADED -> Message.DownloadStatus.NOT_DOWNLOADED
            MessageEntity.DownloadStatus.IN_PROGRESS -> Message.DownloadStatus.IN_PROGRESS
            MessageEntity.DownloadStatus.SAVED_INTERNALLY -> Message.DownloadStatus.SAVED_INTERNALLY
            MessageEntity.DownloadStatus.SAVED_EXTERNALLY -> Message.DownloadStatus.SAVED_EXTERNALLY
            MessageEntity.DownloadStatus.FAILED -> Message.DownloadStatus.FAILED
            null -> Message.DownloadStatus.NOT_DOWNLOADED
        }
    }
}
