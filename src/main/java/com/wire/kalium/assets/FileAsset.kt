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

import kotlin.Throws
import java.io.IOException
import java.util.UUID
import java.io.FileInputStream
import com.waz.model.Messages.GenericMessage
import com.waz.model.Messages.Asset.RemoteData
import com.google.protobuf.ByteString
import com.waz.model.Messages
import com.wire.kalium.tools.Util
import java.io.File

class FileAsset : AssetBase {
    constructor(
        file: File?,
        mimeType: String,
        messageId: UUID
    ) : super(messageId, mimeType, readFile(file)) {}

    constructor(
        assetKey: String?,
        assetToken: String?,
        messageId: UUID,
        mimeType: String,
        bytes: ByteArray?
    ) : super(messageId, mimeType, bytes) {
        this.assetKey = assetKey
        this.assetToken = assetToken
    }

    override fun createGenericMsg(): GenericMessage {
        // Remote
        val remote = RemoteData.newBuilder()
            .setOtrKey(ByteString.copyFrom(otrKey))
            .setSha256(ByteString.copyFrom(sha256))

        // Only set token on private assets
        assetToken?.let { remote.assetToken = it }
        assetKey?.let { remote.assetId = it }

        val asset = Messages.Asset.newBuilder()
            .setExpectsReadConfirmation(readReceiptsEnabled)
            .setUploaded(remote)
        return GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setAsset(asset)
            .build()
    }

    companion object {
        @Throws(IOException::class)
        private fun readFile(file: File?): ByteArray? {
            var bytes: ByteArray?
            FileInputStream(file).use { input -> bytes = Util.toByteArray(input) }
            return bytes
        }
    }
}
