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

import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.EncryptionAlgorithmMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.LinkPreview
import okio.Path.Companion.toPath
import pbandk.ByteArr

interface LinkPreviewMapper {
    fun fromDaoToModel(linkPreview: MessageEntity.LinkPreview): MessageLinkPreview
    fun fromModelToDao(linkPreview: MessageLinkPreview): MessageEntity.LinkPreview
    fun fromProtoToModel(linkPreview: LinkPreview): MessageLinkPreview?
    fun fromModelToProto(linkPreview: MessageLinkPreview): LinkPreview
}

class LinkPreviewMapperImpl(
    private val encryptionAlgorithmMapper: EncryptionAlgorithmMapper = MapperProvider.encryptionAlgorithmMapper(),
) : LinkPreviewMapper {

    override fun fromDaoToModel(linkPreview: MessageEntity.LinkPreview): MessageLinkPreview {
        return MessageLinkPreview(
            url = linkPreview.url,
            urlOffset = linkPreview.urlOffset,
            permanentUrl = linkPreview.permanentUrl,
            title = linkPreview.title,
            summary = linkPreview.summary
        )
    }

    override fun fromModelToDao(linkPreview: MessageLinkPreview): MessageEntity.LinkPreview {
        return MessageEntity.LinkPreview(
            url = linkPreview.url,
            urlOffset = linkPreview.urlOffset,
            // TODO: Check if ?: "" is a good idea here
            permanentUrl = linkPreview.permanentUrl ?: "",
            title = linkPreview.title ?: "",
            summary = linkPreview.summary ?: ""
        )
    }

    override fun fromProtoToModel(linkPreview: LinkPreview): MessageLinkPreview = linkPreview.let {
        val defaultRemoteData = AssetContent.RemoteData(
            otrKey = ByteArray(0),
            sha256 = ByteArray(0),
            assetId = "",
            assetDomain = null,
            assetToken = null,
            encryptionAlgorithm = null
        )
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
                    assetDataPath = "".toPath(),
                    assetToken = linkPreview.image?.uploaded?.assetToken,
                    assetDomain = linkPreview.image?.uploaded?.assetDomain,
                    assetKey = linkPreview.image?.uploaded?.assetId
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
        image = linkPreview.image?.let {
            Asset(
                original = Asset.Original(
                    mimeType = linkPreview.image.mimeType,
                    size = linkPreview.image.assetDataSize,
                    metaData = Asset.Original.MetaData.Image(
                        image = Asset.ImageMetaData(
                            height = linkPreview.image.assetHeight,
                            width = linkPreview.image.assetWidth
                        )
                    )
                ),
                status = Asset.Status.Uploaded(
                    uploaded = Asset.RemoteData(
                        otrKey = ByteArr(linkPreview.image.otrKey.data),
                        sha256 = ByteArr(linkPreview.image.sha256Key.data),
                        assetId = linkPreview.image.assetKey,
                        assetToken = linkPreview.image.assetToken,
                        assetDomain = linkPreview.image.assetDomain,
                        encryption = encryptionAlgorithmMapper.toProtoBufModel(linkPreview.image.encryptionAlgorithm)
                    )
                )
            )
        }
    )
}
