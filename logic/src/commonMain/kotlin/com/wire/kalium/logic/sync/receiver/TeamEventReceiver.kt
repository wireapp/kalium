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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

internal interface TeamEventReceiver : EventReceiver<Event.Team>

internal class TeamEventReceiverImpl(
    private val teamRepository: TeamRepository

) : TeamEventReceiver {

    override suspend fun onEvent(event: Event.Team): Either<CoreFailure, Unit> {
        when (event) {
            is Event.Team.MemberUpdate -> handleMemberUpdate(event)
            is Event.Team.Update -> handleUpdate(event)
        }
        // TODO: Make sure errors are accounted for by each handler.
        //       onEvent now requires Either, so we can propagate errors,
        //       but not all handlers are using it yet.
        //       Returning Either.Right is the equivalent of how it was originally working.
        return Either.Right(Unit)
    }

    private suspend fun handleMemberUpdate(event: Event.Team.MemberUpdate) =
        teamRepository.updateMemberRole(
            teamId = event.teamId,
            userId = event.memberId,
            permissionCode = event.permissionCode,
        )
            .onSuccess {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            }
            .onFailure {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.FAILURE,
                        event,
                        Pair("errorInfo", "$it")
                    )
            }

    private suspend fun handleUpdate(event: Event.Team.Update) =
        teamRepository.updateTeam(
            Team(
                id = event.teamId,
                name = event.name,
                icon = event.icon
            )
        )
            .onSuccess {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            }
            .onFailure {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.FAILURE,
                        event,
                        Pair("errorInfo", "$it")
                    )
            }
}
