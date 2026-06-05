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
package com.wire.kalium.logic.data.message.linkpreview

import com.wire.kalium.logic.data.message.EncryptionAlgorithmMapper
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.LinkPreview
import com.wire.kalium.util.string.hexToByteArray
import com.wire.kalium.util.string.toHexString
import okio.Path.Companion.toPath
import pbandk.ByteArr

internal interface LinkPreviewMapper {
    fun fromDaoToModel(linkPreview: MessageEntity.LinkPreview): MessageLinkPreview
    fun fromModelToDao(linkPreview: MessageLinkPreview): MessageEntity.LinkPreview
    fun fromProtoToModel(linkPreview: LinkPreview): MessageLinkPreview?
    fun fromModelToProto(linkPreview: MessageLinkPreview): LinkPreview
}

internal class LinkPreviewMapperImpl(
    private val encryptionAlgorithmMapper: EncryptionAlgorithmMapper = MapperProvider.encryptionAlgorithmMapper(),
) : LinkPreviewMapper {

    override fun fromDaoToModel(linkPreview: MessageEntity.LinkPreview): MessageLinkPreview {
        val hasRemoteImageData = !linkPreview.imageAssetKey.isNullOrEmpty() ||
                !linkPreview.imageAssetToken.isNullOrEmpty() ||
                !linkPreview.imageAssetDomain.isNullOrEmpty() ||
                !linkPreview.imageOtrKey.isNullOrEmpty() ||
                !linkPreview.imageSha256.isNullOrEmpty() ||
                !linkPreview.imageEncryptionAlgorithm.isNullOrEmpty()
        val image = when {
            !linkPreview.imageLocalPath.isNullOrEmpty() || hasRemoteImageData -> LinkPreviewAsset(
                mimeType = linkPreview.imageMimeType ?: "*/*",
                assetDataPath = linkPreview.imageLocalPath?.takeIf { it.isNotEmpty() }?.toPath(),
                assetDataSize = 0L,
                assetWidth = linkPreview.imageWidth ?: 0,
                assetHeight = linkPreview.imageHeight ?: 0,
                assetKey = linkPreview.imageAssetKey,
                assetToken = linkPreview.imageAssetToken,
                assetDomain = linkPreview.imageAssetDomain,
                otrKey = linkPreview.imageOtrKey?.hexToByteArray() ?: ByteArray(0),
                sha256Key = linkPreview.imageSha256?.hexToByteArray() ?: ByteArray(0),
                encryptionAlgorithm = linkPreview.imageEncryptionAlgorithm.toMessageEncryptionAlgorithm()
            )

            else -> null
        }
        return MessageLinkPreview(
            url = linkPreview.url,
            urlOffset = linkPreview.urlOffset,
            permanentUrl = linkPreview.permanentUrl,
            title = linkPreview.title,
            summary = linkPreview.summary,
            image = image
        )
    }

    override fun fromModelToDao(linkPreview: MessageLinkPreview): MessageEntity.LinkPreview {
        return MessageEntity.LinkPreview(
            url = linkPreview.url,
            urlOffset = linkPreview.urlOffset,
            permanentUrl = linkPreview.permanentUrl ?: "",
            title = linkPreview.title ?: "",
            summary = linkPreview.summary ?: "",
            imageLocalPath = linkPreview.image?.assetDataPath?.toString(),
            imageWidth = linkPreview.image?.assetWidth,
            imageHeight = linkPreview.image?.assetHeight,
            imageMimeType = linkPreview.image?.mimeType,
            imageAssetKey = linkPreview.image?.assetKey,
            imageAssetToken = linkPreview.image?.assetToken,
            imageAssetDomain = linkPreview.image?.assetDomain,
            imageOtrKey = linkPreview.image?.otrKey?.toHexString(),
            imageSha256 = linkPreview.image?.sha256Key?.toHexString(),
            imageEncryptionAlgorithm = linkPreview.image?.encryptionAlgorithm?.name,
        )
    }

    override fun fromProtoToModel(linkPreview: LinkPreview): MessageLinkPreview = linkPreview.let {
        MessageLinkPreview(
            url = linkPreview.url,
            urlOffset = linkPreview.urlOffset,
            permanentUrl = linkPreview.permanentUrl,
            title = linkPreview.title,
            summary = linkPreview.summary,
            image = linkPreview.image?.let {
                LinkPreviewAsset(
                    assetDataSize = linkPreview.image?.original?.size ?: 0,
                    mimeType = linkPreview.image?.original?.mimeType ?: "*/*",
                    assetName = linkPreview.image?.original?.name,
                    assetHeight = when (val metadataType = linkPreview.image?.original?.metaData) {
                        is Asset.Original.MetaData.Image -> metadataType.value.height
                        else -> 0
                    },
                    assetWidth = when (val metadataType = linkPreview.image?.original?.metaData) {
                        is Asset.Original.MetaData.Image -> metadataType.value.width
                        else -> 0
                    },
                    assetDataPath = null,
                    assetToken = linkPreview.image?.uploaded?.assetToken,
                    assetDomain = linkPreview.image?.uploaded?.assetDomain,
                    assetKey = linkPreview.image?.uploaded?.assetId,
                    otrKey = linkPreview.image?.uploaded?.otrKey?.array ?: ByteArray(0),
                    sha256Key = linkPreview.image?.uploaded?.sha256?.array ?: ByteArray(0),
                    encryptionAlgorithm = encryptionAlgorithmMapper.fromProtobufModel(linkPreview.image?.uploaded?.encryption)
                        ?: MessageEncryptionAlgorithm.AES_CBC
                )
            }
        )
    }

    override fun fromModelToProto(linkPreview: MessageLinkPreview): LinkPreview = LinkPreview(
        url = linkPreview.url,
        urlOffset = linkPreview.urlOffset,
        permanentUrl = linkPreview.permanentUrl,
        title = linkPreview.title,
        summary = linkPreview.summary,
        image = linkPreview.image?.let { image ->
            Asset(
                original = Asset.Original(
                    mimeType = image.mimeType,
                    size = image.assetDataSize,
                    metaData = Asset.Original.MetaData.Image(
                        image = Asset.ImageMetaData(
                            height = image.assetHeight,
                            width = image.assetWidth
                        )
                    )
                ),
                status = Asset.Status.Uploaded(
                    uploaded = Asset.RemoteData(
                        otrKey = ByteArr(image.otrKey),
                        sha256 = ByteArr(image.sha256Key),
                        assetId = image.assetKey,
                        assetToken = image.assetToken,
                        assetDomain = image.assetDomain,
                        encryption = encryptionAlgorithmMapper.toProtoBufModel(image.encryptionAlgorithm)
                    )
                )
            )
        }
    )
}

private fun String?.toMessageEncryptionAlgorithm(): MessageEncryptionAlgorithm =
    when (this) {
        MessageEncryptionAlgorithm.AES_GCM.name -> MessageEncryptionAlgorithm.AES_GCM
        else -> MessageEncryptionAlgorithm.AES_CBC
    }
