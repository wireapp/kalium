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

import com.waz.model.Messages;

import java.util.UUID;

@Deprecated
public class MessageAssetBase extends MessageBase {
    // Remote data
    private String assetKey;
    private String assetToken;
    private byte[] otrKey;
    private byte[] sha256;

    // Origin
    private String mimeType;
    private String name;
    private long size;

    public MessageAssetBase(UUID eventId,
                            UUID msgId,
                            UUID convId,
                            String clientId,
                            UUID userId,
                            String time,
                            String assetKey,
                            String assetToken,
                            byte[] otrKey,
                            String mimeType,
                            long size,
                            byte[] sha256,
                            String name) {
        super(eventId, msgId, convId, clientId, userId, time);
        this.assetKey = assetKey;
        this.assetToken = assetToken;
        this.otrKey = otrKey;
        this.mimeType = mimeType;
        this.size = size;
        this.sha256 = sha256;
        this.name = name;
    }

    public MessageAssetBase(UUID eventID, UUID msgId, UUID convId, String clientId, UUID userId, String time) {
        super(eventID, msgId, convId, clientId, userId, time);
    }

    MessageAssetBase(MessageAssetBase base) {
        super(base.eventId, base.messageId, base.conversationId, base.clientId, base.userId, base.time);
        assetKey = base.assetKey;
        assetToken = base.assetToken;
        otrKey = base.otrKey;
        mimeType = base.mimeType;
        size = base.size;
        sha256 = base.sha256;
        name = base.name;
    }

    public MessageAssetBase(MessageBase msg) {
        super(msg);
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getAssetToken() {
        return assetToken;
    }

    public void setAssetToken(String assetToken) {
        this.assetToken = assetToken;
    }

    public void setOtrKey(byte[] otrKey) {
        this.otrKey = otrKey;
    }

    public byte[] getOtrKey() {
        return otrKey;
    }

    public String getAssetKey() {
        return assetKey;
    }

    public void setAssetKey(String assetKey) {
        this.assetKey = assetKey;
    }

    public void setSha256(byte[] sha256) {
        this.sha256 = sha256;
    }

    public byte[] getSha256() {
        return sha256;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void fromRemote(Messages.Asset.RemoteData remoteData) {
        if (remoteData != null) {
            setAssetKey(remoteData.getAssetId());
            setAssetToken(remoteData.hasAssetToken() ? remoteData.getAssetToken() : null);
            setOtrKey(remoteData.getOtrKey().toByteArray());
            setSha256(remoteData.getSha256().toByteArray());
        }
    }

    public void fromOrigin(Messages.Asset.Original original) {
        if (original != null) {
            setMimeType(original.getMimeType());
            setSize(original.getSize());
            setName(original.hasName() ? original.getName() : null);
        }
    }
}
