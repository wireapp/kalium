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
package com.wire.kalium.logic.feature.team

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap

/**
 * This use case is responsible for getting the updated team information of the self user.
 */
class GetUpdatedSelfTeamUseCase internal constructor(
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val teamRepository: TeamRepository,
) {

    suspend operator fun invoke(): Either<CoreFailure, Team?> {
        return selfTeamIdProvider().flatMap { teamId ->
            teamId?.let { teamRepository.syncTeam(it) }
                ?: Either.Right(null)
        }
    }
}
