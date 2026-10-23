/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.calcFileMd5
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.logic.data.message.EncryptionAlgorithmMapper
import com.wire.kalium.logic.data.message.hasValidPendingData
import com.wire.kalium.logic.data.message.hasValidRemoteData
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm.AES_CBC
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm.AES_GCM
import com.wire.kalium.network.api.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.authenticated.asset.AssetResponse
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.persistence.dao.asset.AssetEntity
import com.wire.kalium.persistence.dao.asset.AssetMessageEntity
import com.wire.kalium.persistence.dao.asset.AssetTransferStatusEntity
import com.wire.kalium.persistence.dao.message.AssetPreviewEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.CellAsset
import com.wire.kalium.protobuf.messages.LegalHoldStatus
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import okio.Path
import okio.Path.Companion.toPath
import pbandk.ByteArr

interface AssetMapper {
    fun toMetadataApiModel(uploadAssetMetadata: UploadAssetData, kaliumFileSystem: KaliumFileSystem): AssetMetadataRequest
    fun fromApiUploadResponseToDomainModel(asset: AssetResponse): UploadedAssetId
    fun fromUploadedAssetToDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity
    fun fromUserAssetToDaoModel(assetId: String, assetDomain: String?, dataPath: Path, dataSize: Long): AssetEntity
    fun fromAssetEntityToAssetContent(assetContentEntity: MessageEntityContent.Asset): AssetContent
    fun fromProtoAssetMessageToAssetContent(protoAssetMessage: Asset): AssetContent
    fun fromAssetContentToProtoAssetMessage(
        messageContent: MessageContent.Asset,
        expectsReadConfirmation: Boolean,
        legalHoldStatus: LegalHoldStatus
    ): Asset
}

class AssetMapperImpl(
    private val encryptionAlgorithmMapper: EncryptionAlgorithmMapper = EncryptionAlgorithmMapper(),
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : AssetMapper {
    override fun toMetadataApiModel(uploadAssetMetadata: UploadAssetData, kaliumFileSystem: KaliumFileSystem): AssetMetadataRequest =
        with(dispatcher.io) {
            val dataSource = kaliumFileSystem.source(uploadAssetMetadata.tempEncryptedDataPath)
            val md5 = calcFileMd5(dataSource)
            dataSource.close()
            return AssetMetadataRequest(
                uploadAssetMetadata.assetType,
                uploadAssetMetadata.isPublic,
                AssetRetentionType.valueOf(uploadAssetMetadata.retentionType.name),
                // TODO: pass the md5 to the mapper so we can return Either left in case of any error
                md5 ?: TODO("handle failure"),
                conversationId = uploadAssetMetadata.conversationId,
                filename = uploadAssetMetadata.filename,
                filetype = uploadAssetMetadata.filetype
            )
        }

    override fun fromApiUploadResponseToDomainModel(asset: AssetResponse) =
        UploadedAssetId(key = asset.key, domain = asset.domain, assetToken = asset.token)

    override fun fromUploadedAssetToDaoModel(uploadAssetData: UploadAssetData, uploadedAssetResponse: AssetResponse): AssetEntity {
        return AssetEntity(
            key = uploadedAssetResponse.key,
            domain = uploadedAssetResponse.domain,
            dataPath = uploadAssetData.tempEncryptedDataPath.toString(),
            dataSize = uploadAssetData.dataSize,
            downloadedDate = DateTimeUtil.currentInstant().toEpochMilliseconds()
        )
    }

    override fun fromUserAssetToDaoModel(assetId: String, assetDomain: String?, dataPath: Path, dataSize: Long): AssetEntity {
        return AssetEntity(
            key = assetId,
            domain = assetDomain,
            dataPath = dataPath.toString(),
            dataSize = dataSize,
            downloadedDate = DateTimeUtil.currentInstant().toEpochMilliseconds()
        )
    }

    override fun fromAssetEntityToAssetContent(assetContentEntity: MessageEntityContent.Asset): AssetContent {
        with(assetContentEntity) {
            return AssetContent(
                mimeType = assetMimeType,
                sizeInBytes = assetSizeInBytes,
                name = assetName,
                metadata = getAssetContentMetadata(assetMimeType, assetContentEntity),
                preview = assetPreview?.toModel(),
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
                preview = preview?.toModel(),
                sizeInBytes = original?.size ?: 0,
                name = original?.name,
                mimeType = original?.mimeType ?: "*/*",
                metadata = when (val metadataType = original?.metaData) {
                    is Asset.Original.MetaData.Image -> Image(width = metadataType.value.width, height = metadataType.value.height)
                    is Asset.Original.MetaData.Video -> Video(
                        width = metadataType.value.width,
                        height = metadataType.value.height,
                        durationMs = metadataType.value.durationInMillis
                        )
                    is Asset.Original.MetaData.Audio -> Audio(
                        durationMs = metadataType.value.durationInMillis,
                        normalizedLoudness = metadataType.value.normalizedLoudness?.array
                    )

                    null -> null
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
                                    encryptionAlgorithm = encryptionAlgorithmMapper.fromProtobufModel(encryption)
                                )
                            }
                        }

                        is Asset.Status.NotUploaded -> defaultRemoteData
                    }
                } ?: defaultRemoteData
            )
        }
    }

    override fun fromAssetContentToProtoAssetMessage(
        messageContent: MessageContent.Asset,
        expectsReadConfirmation: Boolean,
        legalHoldStatus: LegalHoldStatus
    ): Asset = with(messageContent.value) {
        Asset(
            preview = preview?.takeIf { it.hasValidRemoteData() }?.toProto(),
            original = Asset.Original(
                mimeType = mimeType,
                size = sizeInBytes,
                name = name,
                metaData = metadata.let {
                    when (it) {
                        is Image -> Asset.Original.MetaData.Image(
                            Asset.ImageMetaData(
                                width = it.width,
                                height = it.height,
                            )
                        )

                        is Audio -> Asset.Original.MetaData.Audio(
                            audio = Asset.AudioMetaData(
                                durationInMillis = it.durationMs,
                                normalizedLoudness = it.normalizedLoudness?.let { ByteArr(it) }
                            )
                        )

                        is Video -> Asset.Original.MetaData.Video(
                            Asset.VideoMetaData(
                                width = it.width,
                                height = it.height,
                                durationInMillis = it.durationMs,
                            )
                        )

                        else -> null
                    }
                }
            ),
            status = Asset.Status.Uploaded(
                uploaded = Asset.RemoteData(
                    otrKey = ByteArr(remoteData.otrKey),
                    sha256 = ByteArr(remoteData.sha256),
                    assetId = remoteData.assetId,
                    assetToken = remoteData.assetToken,
                    assetDomain = remoteData.assetDomain,
                    encryption = encryptionAlgorithmMapper.toProtoBufModel(remoteData.encryptionAlgorithm)
                )
            ),
            expectsReadConfirmation = expectsReadConfirmation,
            legalHoldStatus = legalHoldStatus
        )
    }
}

fun AssetTransferStatus.toDao(): AssetTransferStatusEntity {
    return when (this) {
        AssetTransferStatus.NOT_DOWNLOADED -> AssetTransferStatusEntity.NOT_DOWNLOADED
        AssetTransferStatus.UPLOAD_IN_PROGRESS -> AssetTransferStatusEntity.UPLOAD_IN_PROGRESS
        AssetTransferStatus.DOWNLOAD_IN_PROGRESS -> AssetTransferStatusEntity.DOWNLOAD_IN_PROGRESS
        AssetTransferStatus.UPLOADED -> AssetTransferStatusEntity.UPLOADED
        AssetTransferStatus.SAVED_INTERNALLY -> AssetTransferStatusEntity.SAVED_INTERNALLY
        AssetTransferStatus.SAVED_EXTERNALLY -> AssetTransferStatusEntity.SAVED_EXTERNALLY
        AssetTransferStatus.FAILED_UPLOAD -> AssetTransferStatusEntity.FAILED_UPLOAD
        AssetTransferStatus.FAILED_DOWNLOAD -> AssetTransferStatusEntity.FAILED_DOWNLOAD
        AssetTransferStatus.NOT_FOUND -> AssetTransferStatusEntity.NOT_FOUND
    }
}

fun AssetTransferStatusEntity.toModel(): AssetTransferStatus {
    return when (this) {
        AssetTransferStatusEntity.NOT_DOWNLOADED -> AssetTransferStatus.NOT_DOWNLOADED
        AssetTransferStatusEntity.UPLOAD_IN_PROGRESS -> AssetTransferStatus.UPLOAD_IN_PROGRESS
        AssetTransferStatusEntity.DOWNLOAD_IN_PROGRESS -> AssetTransferStatus.DOWNLOAD_IN_PROGRESS
        AssetTransferStatusEntity.UPLOADED -> AssetTransferStatus.UPLOADED
        AssetTransferStatusEntity.SAVED_INTERNALLY -> AssetTransferStatus.SAVED_INTERNALLY
        AssetTransferStatusEntity.SAVED_EXTERNALLY -> AssetTransferStatus.SAVED_EXTERNALLY
        AssetTransferStatusEntity.FAILED_UPLOAD -> AssetTransferStatus.FAILED_UPLOAD
        AssetTransferStatusEntity.FAILED_DOWNLOAD -> AssetTransferStatus.FAILED_DOWNLOAD
        AssetTransferStatusEntity.NOT_FOUND -> AssetTransferStatus.NOT_FOUND
    }
}

fun AssetMessageEntity.toModel(): AssetMessage {
    return AssetMessage(
        time,
        username,
        messageId,
        conversationId.toModel(),
        assetId,
        width,
        height,
        assetPath = assetPath?.toPath(),
        isSelfAsset = isSelfAsset
    )
}

fun AssetContent.AssetMetadata.toProto(): CellAsset.InitialMetaData<*> =
    when (this) {
        is Image -> CellAsset.InitialMetaData.Image(
            image = CellAsset.ImageMetaData(
                width = width,
                height = height
            )
        )
        is Audio -> CellAsset.InitialMetaData.Audio(
            audio = CellAsset.AudioMetaData(durationMs)
        )
        is Video -> CellAsset.InitialMetaData.Video(
            video = CellAsset.VideoMetaData(
                width = width,
                height = height,
                durationInMillis = durationMs
            )
        )
    }

fun CellAsset.InitialMetaData<*>.toModel(): AssetContent.AssetMetadata =
    when (this) {
        is CellAsset.InitialMetaData.Image -> Image(
            width = value.width,
            height = value.height
        )
        is CellAsset.InitialMetaData.Audio -> Audio(
            durationMs = value.durationInMillis,
            normalizedLoudness = null,
        )
        is CellAsset.InitialMetaData.Video -> Video(
            width = value.width,
            height = value.height,
            durationMs = value.durationInMillis
        )
    }

/** Wire representation; persistence uses independent typed metadata. */
fun AssetContent.Preview.toProto(): Asset.Preview = Asset.Preview(
    mimeType = mimeType,
    size = sizeInBytes,
    remote = Asset.RemoteData(
        otrKey = ByteArr(remoteData.otrKey),
        sha256 = ByteArr(remoteData.sha256),
        assetId = remoteData.assetId,
        assetDomain = remoteData.assetDomain,
        assetToken = remoteData.assetToken,
        encryption = EncryptionAlgorithmMapper().toProtoBufModel(remoteData.encryptionAlgorithm),
    ),
    metaData = Asset.Preview.MetaData.Image(Asset.ImageMetaData(width, height)),
)

@Suppress("ReturnCount")
private fun Asset.Preview.toModel(): AssetContent.Preview? {
    val data = remote ?: return null
    val image = (metaData as? Asset.Preview.MetaData.Image)?.value ?: return null
    return AssetContent.Preview(
        mimeType = mimeType,
        sizeInBytes = size,
        width = image.width,
        height = image.height,
        remoteData = AssetContent.RemoteData(
            otrKey = data.otrKey.array,
            sha256 = data.sha256.array,
            assetId = data.assetId.orEmpty(),
            assetToken = data.assetToken,
            assetDomain = data.assetDomain,
            encryptionAlgorithm = EncryptionAlgorithmMapper().fromProtobufModel(data.encryption),
        ),
    ).takeIf { it.hasValidRemoteData() }
}

internal fun AssetContent.Preview.toDao(): AssetPreviewEntity = AssetPreviewEntity(
    mimeType = mimeType,
    size = sizeInBytes,
    width = width,
    height = height,
    id = remoteData.assetId,
    domain = remoteData.assetDomain,
    token = remoteData.assetToken,
    otrKey = remoteData.otrKey,
    sha256 = remoteData.sha256,
    encryptionAlgorithm = remoteData.encryptionAlgorithm?.name,
)

private fun AssetPreviewEntity.toModel(): AssetContent.Preview? {
    return AssetContent.Preview(
        mimeType = mimeType,
        sizeInBytes = size,
        width = width,
        height = height,
        remoteData = AssetContent.RemoteData(
            otrKey = otrKey,
            sha256 = sha256,
            assetId = id,
            assetToken = token,
            assetDomain = domain,
            encryptionAlgorithm = when (encryptionAlgorithm) {
                "AES_CBC" -> AES_CBC
                "AES_GCM" -> AES_GCM
                else -> null
            },
        ),
    ).takeIf { it.hasValidRemoteData() || it.hasValidPendingData() }
}
