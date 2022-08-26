package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.ParticipantChangedHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallClient
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallMember
import com.wire.kalium.logic.data.call.CallParticipants
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.call.mapper.ParticipantMapper
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Suppress("LongParameterList")
class OnParticipantListChanged(
    private val handle: Handle,
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val participantMapper: ParticipantMapper,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val callingScope: CoroutineScope
) : ParticipantChangedHandler {

    override fun onParticipantChanged(remoteConversationIdString: String, data: String, arg: Pointer?) {

        val participantsChange = Json.decodeFromString<CallParticipants>(data)

        callingScope.launch {
            val participants = mutableListOf<Participant>()
            val clients = mutableListOf<CallClient>()
            val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(remoteConversationIdString)

            val memberList: List<Conversation.Member> = conversationRepository
                .observeConversationMembers(conversationIdWithDomain)
                .first()

            participantsChange.members.map { member ->
                val participant = participantMapper.fromCallMemberToParticipant(member)
                val userId = mapQualifiedMemberId(memberList, member)
                userRepository.getKnownUser(userId).first() {
                    val updatedParticipant = participant.copy(
                        name = it?.name!!,
                        avatarAssetId = it.completePicture,
                        userType = it.userType
                    )
                    participants.add(updatedParticipant)
                }

                clients.add(participantMapper.fromCallMemberToCallClient(member))
            }

            callRepository.updateCallParticipants(
                conversationId = conversationIdWithDomain.toString(),
                participants = participants
            )
            calling.wcall_request_video_streams(
                inst = handle,
                conversationId = remoteConversationIdString,
                mode = DEFAULT_REQUEST_VIDEO_STREAMS_MODE,
                json = CallClientList(clients = clients).toJsonString()
            )
            callingLogger.i(
                "[onParticipantsChanged] - Total Participants: ${participants.size} | ConversationId: $remoteConversationIdString"
            )
        }
    }

    private fun mapQualifiedMemberId(memberList: List<Conversation.Member>, member: CallMember) =
        memberList.first {
            val userId = qualifiedIdMapper.fromStringToQualifiedID(member.userId)
            it.id.value == userId.value
        }.id

    private companion object {
        private const val DEFAULT_REQUEST_VIDEO_STREAMS_MODE = 0
    }
}
