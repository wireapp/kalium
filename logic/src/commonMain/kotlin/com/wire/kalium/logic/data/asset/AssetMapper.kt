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
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm.AES_CBC
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm.AES_GCM
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.authenticated.asset.AssetResponse
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.persistence.dao.asset.AssetEntity
import com.wire.kalium.persistence.dao.asset.AssetMessageEntity
import com.wire.kalium.persistence.dao.asset.AssetTransferStatusEntity
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
    private val encryptionAlgorithmMapper: EncryptionAlgorithmMapper = MapperProvider.encryptionAlgorithmMapper(),
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
                md5 ?: TODO("handle failure")
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
                localData = assetDataPath?.let { AssetContent.LocalData(it) }
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
                    is Asset.Original.MetaData.Audio -> Audio(
                        durationMs = metadataType.value.durationInMillis,
                        normalizedLoudness = metadataType.value.normalizedLoudness?.array
                    )

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
