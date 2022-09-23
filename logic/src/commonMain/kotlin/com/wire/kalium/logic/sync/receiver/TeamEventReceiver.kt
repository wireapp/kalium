package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger

interface TeamEventReceiver : EventReceiver<Event.Team>

class TeamEventReceiverImpl(
    private val teamRepository: TeamRepository
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

    private suspend fun handleMemberLeave(event: Event.Team.MemberLeave) =
        teamRepository.removeTeamMember(
            teamId = event.teamId,
            userId = event.memberId,
        )
            .onFailure { kaliumLogger.e("$TAG - failure on member leave event: $it") }

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
