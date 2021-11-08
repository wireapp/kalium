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
package com.wire.kalium.models

import java.util.UUID
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class LinkPreviewMessage : ImageMessage {
    @JsonProperty
    private var summary: String? = null

    @JsonProperty
    private var title: String? = null

    @JsonProperty
    private var url: String? = null

    @JsonProperty
    private var text: String? = null

    @JsonProperty
    private var urlOffset = 0

    @JsonCreator
    constructor(
        @JsonProperty("eventId") eventId: UUID?,
        @JsonProperty("messageId") messageId: UUID?,
        @JsonProperty("conversationId") convId: UUID?,
        @JsonProperty("clientId") clientId: String?,
        @JsonProperty("userId") userId: UUID?,
        @JsonProperty("time") time: String?,
        @JsonProperty("assetKey") assetKey: String?,
        @JsonProperty("assetToken") assetToken: String?,
        @JsonProperty("otrKey") otrKey: ByteArray?,
        @JsonProperty("mimeType") mimeType: String?,
        @JsonProperty("size") size: Long,
        @JsonProperty("sha256") sha256: ByteArray?,
        @JsonProperty("name") name: String?
    ) : super(eventId, messageId, convId, clientId, userId, time, assetKey, assetToken, otrKey, mimeType, size, sha256, name) {
    }

    constructor(msg: MessageBase?) : super(msg) {}

    fun getSummary(): String? {
        return summary
    }

    fun setSummary(summary: String?) {
        this.summary = summary
    }

    fun getTitle(): String? {
        return title
    }

    fun setTitle(title: String?) {
        this.title = title
    }

    fun getUrl(): String? {
        return url
    }

    fun setUrl(url: String?) {
        this.url = url
    }

    fun getText(): String? {
        return text
    }

    fun setText(text: String?) {
        this.text = text
    }

    fun getUrlOffset(): Int {
        return urlOffset
    }

    fun setUrlOffset(urlOffset: Int) {
        this.urlOffset = urlOffset
    }
}
