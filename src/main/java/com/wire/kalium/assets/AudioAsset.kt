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
package com.wire.kalium.assets

import java.util.UUID
import com.waz.model.Messages.GenericMessage
import com.waz.model.Messages.Asset.RemoteData
import com.google.protobuf.ByteString
import com.waz.model.Messages

class AudioAsset : AssetBase {
    constructor(messageId: UUID?, mimeType: String?) : super(messageId, mimeType) {}
    constructor(bytes: ByteArray?, preview: AudioPreview?) : super(preview.getMessageId(), preview.getMimeType(), bytes) {}

    override fun createGenericMsg(): GenericMessage? {
        val remote = RemoteData.newBuilder()
            .setOtrKey(ByteString.copyFrom(getOtrKey()))
            .setSha256(ByteString.copyFrom(getSha256()))

        // Only set token on private assets
        if (getAssetToken() != null) {
            remote.assetToken = getAssetToken()
        }
        if (getAssetKey() != null) {
            remote.assetId = getAssetKey()
        }
        val asset = Messages.Asset.newBuilder()
            .setUploaded(remote.build())
            .setExpectsReadConfirmation(isReadReceiptsEnabled)
            .build()
        return GenericMessage.newBuilder()
            .setMessageId(getMessageId().toString())
            .setAsset(asset)
            .build()
    }
}
