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
import com.waz.model.Messages.Asset.RemoteData

@Deprecated("")
open class MessageAssetBase : MessageBase {
    // Remote data
    private var assetKey: String? = null
    private var assetToken: String? = null
    private var otrKey: ByteArray?
    private var sha256: ByteArray?

    // Origin
    private var mimeType: String? = null
    private var name: String? = null
    private var size: Long = 0

    constructor(
        eventId: UUID?,
        msgId: UUID?,
        convId: UUID?,
        clientId: String?,
        userId: UUID?,
        time: String?,
        assetKey: String?,
        assetToken: String?,
        otrKey: ByteArray?,
        mimeType: String?,
        size: Long,
        sha256: ByteArray?,
        name: String?
    ) : super(eventId, msgId, convId, clientId, userId, time) {
        this.assetKey = assetKey
        this.assetToken = assetToken
        this.otrKey = otrKey
        this.mimeType = mimeType
        this.size = size
        this.sha256 = sha256
        this.name = name
    }

    constructor(eventID: UUID?, msgId: UUID?, convId: UUID?, clientId: String?, userId: UUID?, time: String?) : super(
        eventID,
        msgId,
        convId,
        clientId,
        userId,
        time
    ) {
    }

    internal constructor(base: MessageAssetBase?) : super(
        base.eventId,
        base.messageId,
        base.conversationId,
        base.clientId,
        base.userId,
        base.time
    ) {
        assetKey = base.assetKey
        assetToken = base.assetToken
        otrKey = base.otrKey
        mimeType = base.mimeType
        size = base.size
        sha256 = base.sha256
        name = base.name
    }

    constructor(msg: MessageBase?) : super(msg) {}

    fun setSize(size: Long) {
        this.size = size
    }

    fun getSize(): Long {
        return size
    }

    fun getMimeType(): String? {
        return mimeType
    }

    fun setMimeType(mimeType: String?) {
        this.mimeType = mimeType
    }

    fun getAssetToken(): String? {
        return assetToken
    }

    fun setAssetToken(assetToken: String?) {
        this.assetToken = assetToken
    }

    fun setOtrKey(otrKey: ByteArray?) {
        this.otrKey = otrKey
    }

    fun getOtrKey(): ByteArray? {
        return otrKey
    }

    fun getAssetKey(): String? {
        return assetKey
    }

    fun setAssetKey(assetKey: String?) {
        this.assetKey = assetKey
    }

    fun setSha256(sha256: ByteArray?) {
        this.sha256 = sha256
    }

    fun getSha256(): ByteArray? {
        return sha256
    }

    fun getName(): String? {
        return name
    }

    fun setName(name: String?) {
        this.name = name
    }

    fun fromRemote(remoteData: RemoteData?) {
        if (remoteData != null) {
            setAssetKey(remoteData.assetId)
            setAssetToken(if (remoteData.hasAssetToken()) remoteData.assetToken else null)
            setOtrKey(remoteData.otrKey.toByteArray())
            setSha256(remoteData.sha256.toByteArray())
        }
    }

    fun fromOrigin(original: Original?) {
        if (original != null) {
            setMimeType(original.mimeType)
            setSize(original.size)
            setName(if (original.hasName()) original.name else null)
        }
    }
}
