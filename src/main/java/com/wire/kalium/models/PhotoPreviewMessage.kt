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
import com.waz.model.Messages.Asset.Original
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class PhotoPreviewMessage : OriginMessage {
    @JsonProperty
    private var height = 0

    @JsonProperty
    private var width = 0

    @JsonCreator
    constructor(
        @JsonProperty("eventId") eventId: UUID?,
        @JsonProperty("messageId") messageId: UUID?,
        @JsonProperty("conversationId") convId: UUID?,
        @JsonProperty("clientId") clientId: String?,
        @JsonProperty("userId") userId: UUID?,
        @JsonProperty("time") time: String?,
        @JsonProperty("mimeType") mimeType: String?,
        @JsonProperty("size") size: Long,
        @JsonProperty("name") name: String?,
        @JsonProperty("width") width: Int,
        @JsonProperty("height") height: Int
    ) : super(eventId, messageId, convId, clientId, userId, time) {
        setMimeType(mimeType)
        setName(name)
        setSize(size)
        setWidth(width)
        setHeight(height)
    }

    constructor(msg: MessageBase?, original: Original?) : super(msg) {
        mimeType = original.getMimeType()
        size = original.getSize()
        name = original.getName()
        setWidth(original.getImage().width)
        setHeight(original.getImage().height)
    }

    fun getHeight(): Int {
        return height
    }

    fun setHeight(height: Int) {
        this.height = height
    }

    fun getWidth(): Int {
        return width
    }

    fun setWidth(width: Int) {
        this.width = width
    }
}
