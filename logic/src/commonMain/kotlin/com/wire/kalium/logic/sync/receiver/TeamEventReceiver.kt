package com.wire.kalium.logic.sync.receiver

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

interface TeamEventReceiver : EventReceiver<Event.Team>

internal class TeamEventReceiverImpl(
    private val teamRepository: TeamRepository,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId,
) : TeamEventReceiver {

    override suspend fun onEvent(event: Event.Team) {
        when (event) {
            is Event.Team.MemberJoin -> handleMemberJoin(event)
            is Event.Team.MemberLeave -> handleMemberLeave(event)
            is Event.Team.MemberUpdate -> handleMemberUpdate(event)
            is Event.Team.Update -> handleUpdate(event)
        }
    }

    private suspend fun handleMemberJoin(event: Event.Team.MemberJoin) =
        teamRepository.fetchTeamMember(
            teamId = event.teamId,
            userId = event.memberId,
        )
            .onFailure { kaliumLogger.e("$TAG - failure on member join event: $it") }

    private suspend fun handleMemberLeave(event: Event.Team.MemberLeave) {
        val userId = UserId(event.memberId, selfUserId.domain)
        teamRepository.removeTeamMember(
            teamId = event.teamId,
            userId = event.memberId,
        )
            .onSuccess {
                val knownUser = userRepository.getKnownUser(userId).first()
                if (knownUser?.name != null) {
                    val conversationIds = conversationRepository.getConversationIdsByUserId(userId)
                    conversationIds.onSuccess {
                        it.forEach { conversationId ->
                            val message = Message.System(
                                id = uuid4().toString(), // We generate a random uuid for this new system message
                                content = MessageContent.TeamMemberRemoved(knownUser.name),
                                conversationId = conversationId,
                                date = event.timestampIso,
                                senderUserId = userId,
                                status = Message.Status.SENT,
                                visibility = Message.Visibility.VISIBLE
                            )
                            persistMessage(message)
                        }
                    }
                }
            }
            .onSuccess { conversationRepository.deleteUserFromConversations(userId) }
            .onFailure { kaliumLogger.e("$TAG - failure on member leave event: $it") }
    }

    private suspend fun handleMemberUpdate(event: Event.Team.MemberUpdate) =
        teamRepository.updateMemberRole(
            teamId = event.teamId,
            userId = event.memberId,
            permissionCode = event.permissionCode,
        )
            .onFailure { kaliumLogger.e("$TAG - failure on member update event: $it") }

    private suspend fun handleUpdate(event: Event.Team.Update) =
        teamRepository.updateTeam(
            Team(
                id = event.teamId,
                name = event.name,
                icon = event.icon
            )
        )
            .onFailure { kaliumLogger.e("$TAG - failure on team update event: $it") }

    private companion object {
        const val TAG = "TeamEventReceiver"
    }
}
