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
package com.wire.kalium.models.outbound

import com.google.protobuf.ByteString
import com.waz.model.Messages
import com.waz.model.Messages.Asset.RemoteData
import com.waz.model.Messages.GenericMessage
import java.util.*

class VideoAsset(messageId: UUID, mimeType: String, bytes: ByteArray) : AssetBase(messageId, mimeType, bytes) {
    override fun createGenericMsg(): GenericMessage {
        val remote = RemoteData.newBuilder()
            .setOtrKey(ByteString.copyFrom(otrKey))
            .setSha256(ByteString.copyFrom(sha256))

        // Only set token on private assets
        assetToken?.let { remote.assetToken = it }
        assetKey?.let { remote.assetId = it }

        val asset = Messages.Asset.newBuilder()
            .setUploaded(remote.build())
            .setExpectsReadConfirmation(readReceiptsEnabled)
            .build()
        return GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setAsset(asset)
            .build()
    }
}
