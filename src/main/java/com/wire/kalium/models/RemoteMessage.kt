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
import com.waz.model.Messages.Asset.RemoteData
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonCreator

class RemoteMessage : MessageBase {
    @JsonProperty
    private var assetId: String? = null

    @JsonProperty
    private var assetToken: String? = null

    @JsonProperty
    private var otrKey: ByteArray?

    @JsonProperty
    private var sha256: ByteArray?

    @JsonCreator
    constructor(
        @JsonProperty("eventId") eventId: UUID?,
        @JsonProperty("messageId") messageId: UUID?,
        @JsonProperty("conversationId") convId: UUID?,
        @JsonProperty("clientId") clientId: String?,
        @JsonProperty("userId") userId: UUID?,
        @JsonProperty("time") time: String?,
        @JsonProperty("assetId") assetId: String?,
        @JsonProperty("assetToken") assetToken: String?,
        @JsonProperty("otrKey") otrKey: ByteArray?,
        @JsonProperty("sha256") sha256: ByteArray?
    ) : super(eventId, messageId, convId, clientId, userId, time) {
        setAssetId(assetId)
        setAssetToken(assetToken)
        setSha256(sha256)
        setOtrKey(otrKey)
    }

    constructor(msg: MessageBase?, uploaded: RemoteData?) : super(msg) {
        setAssetId(uploaded.getAssetId())
        setAssetToken(uploaded.getAssetToken())
        setSha256(uploaded.getSha256().toByteArray())
        setOtrKey(uploaded.getOtrKey().toByteArray())
    }

    fun getAssetToken(): String? {
        return assetToken
    }

    fun setAssetToken(assetToken: String?) {
        this.assetToken = assetToken
    }

    fun getOtrKey(): ByteArray? {
        return otrKey
    }

    fun setOtrKey(otrKey: ByteArray?) {
        this.otrKey = otrKey
    }

    fun getAssetId(): String? {
        return assetId
    }

    fun setAssetId(assetId: String?) {
        this.assetId = assetId
    }

    fun getSha256(): ByteArray? {
        return sha256
    }

    fun setSha256(sha256: ByteArray?) {
        this.sha256 = sha256
    }
}
