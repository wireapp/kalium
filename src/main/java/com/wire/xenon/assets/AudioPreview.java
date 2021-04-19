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

package com.wire.xenon.assets;

import com.google.protobuf.ByteString;
import com.waz.model.Messages;

import java.util.UUID;

public class AudioPreview implements IGeneric {
    private final String name;
    private final String mimeType;
    private final UUID messageId;
    private final long duration;
    private final int size;
    private final byte[] levels;

    public AudioPreview(String name, String mimeType, long duration, byte[] levels, int size) {
        this.name = name;
        this.mimeType = mimeType;
        this.messageId = UUID.randomUUID();
        this.duration = duration;
        this.size = size;
        this.levels = levels;
    }

    public AudioPreview(String name, String mimeType, long duration, int size) {
        this(name, mimeType, duration, null, size);
    }

    @Override
    public Messages.GenericMessage createGenericMsg() {
        Messages.Asset.AudioMetaData.Builder audio = Messages.Asset.AudioMetaData.newBuilder()
                .setDurationInMillis(duration);

        if (levels != null)
            audio.setNormalizedLoudness(ByteString.copyFrom(levels));

        Messages.Asset.Original.Builder original = Messages.Asset.Original.newBuilder()
                .setSize(size)
                .setName(name)
                .setMimeType(mimeType)
                .setAudio(audio.build());

        Messages.Asset asset = Messages.Asset.newBuilder()
                .setOriginal(original.build())
                .build();

        return Messages.GenericMessage.newBuilder()
                .setMessageId(getMessageId().toString())
                .setAsset(asset)
                .build();
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getDuration() {
        return duration;
    }

    public byte[] getLevels() {
        return levels;
    }

    @Override
    public UUID getMessageId() {
        return messageId;
    }
}
