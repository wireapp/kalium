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
import com.waz.model.Messages.Asset.AudioMetaData
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonCreator

@JsonIgnoreProperties(ignoreUnknown = true)
@Deprecated("")
class AudioMessage : MessageAssetBase {
    @JsonProperty
    private var duration: Long = 0

    @JsonProperty
    private var levels: ByteArray?

    @JsonCreator
    constructor(
        @JsonProperty("eventId") eventId: UUID?,
        @JsonProperty("messageId") messageId: UUID?,
        @JsonProperty("conversationId") convId: UUID?,
        @JsonProperty("clientId") clientId: String?,
        @JsonProperty("userId") userId: UUID?,
        @JsonProperty("time") time: String?
    ) : super(eventId, messageId, convId, clientId, userId, time) {
    }

    constructor(base: MessageAssetBase?, audio: AudioMetaData?) : super(base) {
        setDuration(audio.getDurationInMillis())
        if (audio.hasNormalizedLoudness()) levels = audio.getNormalizedLoudness().toByteArray()
    }

    fun setDuration(duration: Long) {
        this.duration = duration
    }

    fun getDuration(): Long {
        return duration
    }

    fun getLevels(): ByteArray? {
        return levels
    }

    fun setLevels(levels: ByteArray?) {
        this.levels = levels
    }
}
