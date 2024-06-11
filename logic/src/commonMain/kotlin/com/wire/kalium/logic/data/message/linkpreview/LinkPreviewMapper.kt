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

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.LinkPreview
import pbandk.ByteArr

interface LinkPreviewMapper {
    // fun fromDaoToModel(linkPreview: MessageEntity.LinkPreview): MessageLinkPreview
    // fun fromModelToDao(linkPreview: MessageLinkPreview): MessageEntity.LinkPreview
    fun fromProtoToModel(linkPreview: LinkPreview): MessageLinkPreview?
    fun fromModelToProto(linkPreview: MessageLinkPreview): LinkPreview
}

class LinkPreviewMapperImpl(
    private val idMapper: IdMapper,
    private val selfUserId: UserId
) : LinkPreviewMapper {

    /*
    override fun fromDaoToModel(mention: MessageEntity.Mention): MessageLinkPreview {
        return mention.toModel(selfUserId)
    }

    override fun fromModelToDao(mention: MessageMention): MessageEntity.Mention {
        return mention.toDao()
    }*/

    override fun fromProtoToModel(linkPreview: LinkPreview): MessageLinkPreview? = linkPreview.let {
        MessageLinkPreview(
            url = linkPreview.url,
            urlOffset = linkPreview.urlOffset,
            permanentUrl = linkPreview.permanentUrl,
            title = linkPreview.title,
            summary = linkPreview.summary
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
                preview = Asset.Preview(
                    mimeType = linkPreview.image.mimeType,
                    size = linkPreview.image.assetDataSize,
                    remote = Asset.RemoteData(
                        assetId = linkPreview.image.assetId.key,
                        assetToken = linkPreview.image.assetId.assetToken,
                        assetDomain = linkPreview.image.assetId.domain,
                        otrKey = ByteArr(linkPreview.image.otrKey.data),
                        sha256 = ByteArr(linkPreview.image.sha256Key.data)
                    )
                )
            )
        }
    )
}

