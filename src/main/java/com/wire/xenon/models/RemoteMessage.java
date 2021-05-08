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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.waz.model.Messages;

import java.util.UUID;

public class RemoteMessage extends MessageBase {
    @JsonProperty
    private String assetId;
    @JsonProperty
    private String assetToken;
    @JsonProperty
    private byte[] otrKey;
    @JsonProperty
    private byte[] sha256;

    @JsonCreator
    public RemoteMessage(@JsonProperty("eventId") UUID eventId,
                         @JsonProperty("messageId") UUID messageId,
                         @JsonProperty("conversationId") UUID convId,
                         @JsonProperty("clientId") String clientId,
                         @JsonProperty("userId") UUID userId,
                         @JsonProperty("time") String time,
                         @JsonProperty("assetId") String assetId,
                         @JsonProperty("assetToken") String assetToken,
                         @JsonProperty("otrKey") byte[] otrKey,
                         @JsonProperty("sha256") byte[] sha256) {
        super(eventId, messageId, convId, clientId, userId, time);

        setAssetId(assetId);
        setAssetToken(assetToken);
        setSha256(sha256);
        setOtrKey(otrKey);
    }

    public RemoteMessage(MessageBase msg, Messages.Asset.RemoteData uploaded) {
        super(msg);

        setAssetId(uploaded.getAssetId());
        setAssetToken(uploaded.getAssetToken());
        setSha256(uploaded.getSha256().toByteArray());
        setOtrKey(uploaded.getOtrKey().toByteArray());
    }

    public String getAssetToken() {
        return assetToken;
    }

    public void setAssetToken(String assetToken) {
        this.assetToken = assetToken;
    }

    public byte[] getOtrKey() {
        return otrKey;
    }

    public void setOtrKey(byte[] otrKey) {
        this.otrKey = otrKey;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public byte[] getSha256() {
        return sha256;
    }

    public void setSha256(byte[] sha256) {
        this.sha256 = sha256;
    }
}
