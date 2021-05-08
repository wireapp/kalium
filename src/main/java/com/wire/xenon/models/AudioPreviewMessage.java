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
import com.waz.model.Messages;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AudioPreviewMessage extends OriginMessage {
    @JsonProperty
    private long duration;

    @JsonProperty
    private byte[] levels;

    @JsonCreator
    public AudioPreviewMessage(@JsonProperty("eventId") UUID eventId,
                               @JsonProperty("messageId") UUID messageId,
                               @JsonProperty("conversationId") UUID convId,
                               @JsonProperty("clientId") String clientId,
                               @JsonProperty("userId") UUID userId,
                               @JsonProperty("time") String time,
                               @JsonProperty("mimeType") String mimeType,
                               @JsonProperty("size") long size,
                               @JsonProperty("name") String name,
                               @JsonProperty("duration") long duration,
                               @JsonProperty("levels") byte[] levels) {
        super(eventId, messageId, convId, clientId, userId, time);

        setMimeType(mimeType);
        setName(name);
        setSize(size);

        setDuration(duration);
        setLevels(levels);
    }

    public AudioPreviewMessage(MessageBase msg, Messages.Asset.Original original) {
        super(msg);

        setMimeType(original.getMimeType());
        setSize(original.getSize());
        setName(original.getName());
        setDuration(original.getAudio().getDurationInMillis());
        setLevels(original.getAudio().getNormalizedLoudness().toByteArray());
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public byte[] getLevels() {
        return levels;
    }

    public void setLevels(byte[] levels) {
        this.levels = levels;
    }
}
