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

import com.wire.kalium.logic.data.call.CallMember
import com.wire.kalium.logic.data.call.ParticipantMinimized
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import io.mockative.Mockable

@Mockable
interface ParticipantMapper {
    fun fromCallMemberToParticipantMinimized(member: CallMember): ParticipantMinimized
}

class ParticipantMapperImpl(
    private val videoStateChecker: VideoStateChecker,
    private val callMapper: CallMapper,
    private val qualifiedIdMapper: QualifiedIdMapper
) : ParticipantMapper {

    override fun fromCallMemberToParticipantMinimized(member: CallMember): ParticipantMinimized = with(member) {
        val videoState = callMapper.fromIntToCallingVideoState(vrecv)
        val isCameraOn = videoStateChecker.isCameraOn(videoState)
        val isSharingScreen = videoStateChecker.isSharingScreen(videoState)

        ParticipantMinimized(
            id = QualifiedID(value = member.userId.removeDomain(), domain = member.userId.getDomain()),
            userId = qualifiedIdMapper.fromStringToQualifiedID(member.userId),
            clientId = clientId,
            isMuted = isMuted == 1,
            isCameraOn = isCameraOn,
            isSharingScreen = isSharingScreen,
            hasEstablishedAudio = aestab == 1
        )
    }

    private companion object {
        private const val DOMAIN_SEPARATOR = "@"

        private fun String.removeDomain() = if (contains(DOMAIN_SEPARATOR)) split(DOMAIN_SEPARATOR).first() else this

        private fun String.getDomain() = if (contains(DOMAIN_SEPARATOR)) split(DOMAIN_SEPARATOR).last() else ""
    }
}
