package com.wire.kalium.logic.data.call.mapper

import com.wire.kalium.logic.data.call.CallClient
import com.wire.kalium.logic.data.call.CallMember
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.id.QualifiedID

interface ParticipantMapper {
    fun fromCallMemberToParticipant(member: CallMember): Participant
    fun fromCallMemberToCallClient(member: CallMember): CallClient
}

class ParticipantMapperImpl(
    private val videoStateChecker: VideoStateChecker,
    private val callMapper: CallMapper
) : ParticipantMapper {

    override fun fromCallMemberToParticipant(member: CallMember): Participant = with(member) {
        val videoState = callMapper.fromIntToCallingVideoState(vrecv)
        val isCameraOn = videoStateChecker.isCameraOn(videoState)
        val isSharingScreen = videoStateChecker.isSharingScreen(videoState)

        Participant(
            id = QualifiedID(
                value = userId.removeDomain(),
                domain = userId.getDomain()
            ),
            clientId = clientId,
            isMuted = isMuted == 1,
            isCameraOn = isCameraOn,
            isSharingScreen = isSharingScreen
        )
    }

    override fun fromCallMemberToCallClient(member: CallMember): CallClient = with(member) {
        CallClient(
            userId = QualifiedID(
                value = userId.removeDomain(),
                domain = userId.getDomain()
            ).toString(),
            clientId = clientId
        )
    }

    private companion object {
        private const val DOMAIN_SEPARATOR = "@"

        private fun String.removeDomain() = if (contains(DOMAIN_SEPARATOR)) split(DOMAIN_SEPARATOR).first() else this

        private fun String.getDomain() = if (contains(DOMAIN_SEPARATOR)) split(DOMAIN_SEPARATOR).last() else ""
    }
}
