package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.VideoReceiveStateHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.id.QualifiedIdMapper

class OnParticipantsVideoStateChanged(
    private val callRepository: CallRepository,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val callMapper: CallMapper,
    private val videoStateChecker: VideoStateChecker
) : VideoReceiveStateHandler {
    override fun onVideoReceiveStateChanged(conversationId: String, userId: String, clientId: String, state: Int, arg: Pointer?) {
        callingLogger.i(
            "[onVideoReceiveStateChanged] - conversationId: $conversationId | userId: $userId clientId: $clientId" +
                    " | state: $state"
        )

        val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationId)
        val videoState = callMapper.fromIntToCallingVideoState(state)
        val isCameraOn = videoStateChecker.isCameraOn(videoState)
        val isSharingScreen = videoStateChecker.isSharingScreen(videoState)
        callRepository.updateParticipantCameraStateById(
            conversationIdWithDomain.toString(),
            userId,
            clientId,
            isCameraOn,
            isSharingScreen
        )
    }
}
