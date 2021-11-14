//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//
package com.wire.kalium.models.outbound

import com.waz.model.Messages
import com.waz.model.Messages.Article
import com.waz.model.Messages.GenericMessage
import com.wire.kalium.tools.Logger
import java.util.*

class LinkPreview(
    private val url: String,
    private val title: String?,
    private val thumbnail: Picture?
) : GenericMessageIdentifiable {

    override val messageId: UUID = UUID.randomUUID()

    override fun createGenericMsg(): GenericMessage {
        var preview: Messages.Asset? = null
        try {
            preview = thumbnail?.createGenericMsg()?.asset
        } catch (e: Exception) {
            Logger.warning("LinkPreview: %s", e)
        }

        // Legacy todo: remove it!
        val article = Article.newBuilder()
            .setTitle(title)
            .setPermanentUrl(url)
            .setImage(preview)
            .build()

        // Legacy
        val linkPreview = Messages.LinkPreview.newBuilder()
            .setUrl(url)
            .setUrlOffset(0)
            .setImage(preview)
            .setPermanentUrl(url)
            .setTitle(title)
            .setArticle(article)
        val text = Messages.Text.newBuilder()
            .setContent(url)
            .addLinkPreview(linkPreview)
        return GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setText(text.build())
            .build()
    }
}
