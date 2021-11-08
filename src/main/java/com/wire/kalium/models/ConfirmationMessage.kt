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

@JsonIgnoreProperties(ignoreUnknown = true)
class ConfirmationMessage : MessageBase {
    @JsonProperty
    private var type: Type? = null

    @JsonProperty
    private var confirmationMessageId: UUID? = null

    @JsonCreator
    constructor(
        @JsonProperty("eventId") eventId: UUID?,
        @JsonProperty("messageId") messageId: UUID?,
        @JsonProperty("conversationId") convId: UUID?,
        @JsonProperty("clientId") clientId: String?,
        @JsonProperty("userId") userId: UUID?,
        @JsonProperty("time") time: String?
    ) : super(eventId, messageId, convId, clientId, userId, time) {
    }

    constructor(msg: MessageBase?) : super(msg) {}

    fun getType(): Type? {
        return type
    }

    fun setType(type: Type?) {
        this.type = type
    }

    fun getConfirmationMessageId(): UUID? {
        return confirmationMessageId
    }

    fun setConfirmationMessageId(confirmationMessageId: UUID?) {
        this.confirmationMessageId = confirmationMessageId
    }

    enum class Type {
        DELIVERED, READ
    }
}
