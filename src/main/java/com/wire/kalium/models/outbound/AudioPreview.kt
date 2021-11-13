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
import com.waz.model.Messages.Asset.AudioMetaData
import com.waz.model.Messages.Asset.Original
import com.waz.model.Messages.GenericMessage
import java.util.*

class AudioPreview(
    private val name: String,
    val mimeType: String,
    private val duration: Long,
    private val levels: ByteArray,
    private val size: Int
) : GenericMessageIdentifiable {

    override val messageId: UUID = UUID.randomUUID()

    override fun createGenericMsg(): GenericMessage {
        val audio = AudioMetaData.newBuilder()
            .setDurationInMillis(duration)

        levels.let { audio.normalizedLoudness = ByteString.copyFrom(it) }

        val original = Original.newBuilder()
            .setSize(size.toLong())
            .setName(name)
            .setMimeType(mimeType)
            .setAudio(audio.build())
        val asset = Messages.Asset.newBuilder()
            .setOriginal(original.build())
            .build()
        return GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setAsset(asset)
            .build()
    }
}
