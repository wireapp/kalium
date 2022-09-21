package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.team.TeamRepository

interface TeamEventReceiver : EventReceiver<Event.Team>

class TeamEventReceiverImpl(
    private val teamRepository: TeamRepository
) : TeamEventReceiver {

    override suspend fun onEvent(event: Event.Team) {
        when (event) {
            is Event.Team.MemberJoin -> TODO()
            is Event.Team.MemberLeave -> TODO()
            is Event.Team.MemberUpdate -> TODO()
            is Event.Team.Update -> TODO()
        }
    }

    private companion object {
        const val TAG = "TeamEventReceiver"
    }
}
