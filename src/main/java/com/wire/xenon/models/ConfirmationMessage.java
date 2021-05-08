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

package com.wire.xenon.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfirmationMessage extends MessageBase {
    @JsonProperty
    private Type type;
    @JsonProperty
    private UUID confirmationMessageId;

    @JsonCreator
    public ConfirmationMessage(@JsonProperty("eventId") UUID eventId,
                               @JsonProperty("messageId") UUID messageId,
                               @JsonProperty("conversationId") UUID convId,
                               @JsonProperty("clientId") String clientId,
                               @JsonProperty("userId") UUID userId,
                               @JsonProperty("time") String time) {
        super(eventId, messageId, convId, clientId, userId, time);
    }

    public ConfirmationMessage(MessageBase msg) {
        super(msg);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public UUID getConfirmationMessageId() {
        return confirmationMessageId;
    }

    public void setConfirmationMessageId(UUID confirmationMessageId) {
        this.confirmationMessageId = confirmationMessageId;
    }

    public enum Type {
        DELIVERED,
        READ
    }
}
