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
import com.waz.model.Messages.Asset.Original
import com.google.protobuf.ByteString
import com.waz.model.Messages.Asset.AudioMetaData
import com.waz.model.Messages

class AudioPreview(private val name: String?, private val mimeType: String?, duration: Long, levels: ByteArray?, size: Int) : IGeneric {
    private val messageId: UUID?
    private val duration: Long
    private val size: Int
    private val levels: ByteArray?

    constructor(name: String?, mimeType: String?, duration: Long, size: Int) : this(name, mimeType, duration, null, size) {}

    override fun createGenericMsg(): GenericMessage? {
        val audio = AudioMetaData.newBuilder()
            .setDurationInMillis(duration)
        if (levels != null) audio.normalizedLoudness = ByteString.copyFrom(levels)
        val original = Original.newBuilder()
            .setSize(size.toLong())
            .setName(name)
            .setMimeType(mimeType)
            .setAudio(audio.build())
        val asset = Messages.Asset.newBuilder()
            .setOriginal(original.build())
            .build()
        return GenericMessage.newBuilder()
            .setMessageId(getMessageId().toString())
            .setAsset(asset)
            .build()
    }

    fun getName(): String? {
        return name
    }

    fun getSize(): Int {
        return size
    }

    fun getMimeType(): String? {
        return mimeType
    }

    fun getDuration(): Long {
        return duration
    }

    fun getLevels(): ByteArray? {
        return levels
    }

    override fun getMessageId(): UUID? {
        return messageId
    }

    init {
        messageId = UUID.randomUUID()
        this.duration = duration
        this.size = size
        this.levels = levels
    }
}
