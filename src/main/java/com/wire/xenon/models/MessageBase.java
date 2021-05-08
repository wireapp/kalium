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

import java.util.UUID;

public class MessageBase {
    protected final UUID messageId;
    protected final UUID eventId;
    protected final UUID userId;
    protected final String clientId;
    protected final UUID conversationId;
    protected final String time;

    public MessageBase(UUID eventId, UUID msgId, UUID convId, String clientId, UUID userId, String time) {
        this.eventId = eventId;
        this.messageId = msgId;
        this.conversationId = convId;
        this.clientId = clientId;
        this.userId = userId;
        this.time = time;
    }

    public MessageBase(MessageBase msg) {
        this.eventId = msg.eventId;
        this.messageId = msg.messageId;
        this.conversationId = msg.conversationId;
        this.clientId = msg.clientId;
        this.userId = msg.userId;
        this.time = msg.time;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getClientId() {
        return clientId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public String getTime() {
        return time;
    }

    public UUID getEventId() {
        return eventId;
    }
}
