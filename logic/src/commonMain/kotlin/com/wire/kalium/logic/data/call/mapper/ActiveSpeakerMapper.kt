/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.call.mapper

import com.wire.kalium.logic.data.call.CallActiveSpeakers
import com.wire.kalium.logic.data.call.Participant

interface ActiveSpeakerMapper {
    fun mapParticipantsActiveSpeaker(
        participants: List<Participant>,
        activeSpeakers: CallActiveSpeakers
    ): List<Participant>
}

class ActiveSpeakerMapperImpl : ActiveSpeakerMapper {
    override fun mapParticipantsActiveSpeaker(
        participants: List<Participant>,
        activeSpeakers: CallActiveSpeakers
    ): List<Participant> = participants.toMutableList().apply {
        activeSpeakers.activeSpeakers.forEach { activeSpeaker ->
            find { participant ->
                activeSpeaker.userId == participant.id.toString() && activeSpeaker.clientId == participant.clientId
            }?.let {
                this[indexOf(it)] = it.copy(
                    isSpeaking = activeSpeaker.audioLevel > 0 && activeSpeaker.audioLevelNow > 0
                )
            }
        }
    }
}
