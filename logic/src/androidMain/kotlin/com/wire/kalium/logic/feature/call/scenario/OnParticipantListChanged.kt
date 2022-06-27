package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.ParticipantChangedHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallClient
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.CallParticipants
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class OnParticipantListChanged(
    private val handle: Handle,
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val participantMapper: CallMapper.ParticipantMapper,
    private val userRepository: UserRepository,
    private val callingScope: CoroutineScope
) : ParticipantChangedHandler {

    override fun onParticipantChanged(remoteConversationIdString: String, data: String, arg: Pointer?) {
        val participants = mutableListOf<Participant>()
        val clients = mutableListOf<CallClient>()

        val participantsChange = Json.decodeFromString<CallParticipants>(data)
        callingScope.launch {
            participantsChange.members.map { member ->
                val participant = participantMapper.fromCallMemberToParticipant(member)
                userRepository.getUserInfo(member.userId.toConversationId()).map {
                    val updatedParticipant = participant.copy(
                        name = it.name!!,
                        avatarAssetId = it.completePicture
                    )
                    participants.add(updatedParticipant)
                }

                clients.add(participantMapper.fromCallMemberToCallClient(member))
            }

            callRepository.updateCallParticipants(
                //TODO should be handled properly after supporting federated calls
                conversationId = remoteConversationIdString.toConversationId().toString(),
                participants = participants
            )
        }
        calling.wcall_request_video_streams(
            inst = handle,
            conversationId = remoteConversationIdString,
            mode = DEFAULT_REQUEST_VIDEO_STREAMS_MODE,
            json = CallClientList(clients = clients).toJsonString()
        )
        callingLogger.i("onParticipantsChanged() - wcall_request_video_streams() called")

        callingLogger.i("onParticipantsChanged() - Total Participants: ${participants.size} for $remoteConversationIdString")
    }

    private companion object {
        private const val DEFAULT_REQUEST_VIDEO_STREAMS_MODE = 0
    }
}
