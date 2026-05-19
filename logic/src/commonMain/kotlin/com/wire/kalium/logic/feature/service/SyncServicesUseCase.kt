/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.service

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.team.TeamRepository

/**
 * Syncs the list of services available for the current self-team from the backend.
 * No-op when the self user does not belong to a team.
 */
public interface SyncServicesUseCase {

    public suspend operator fun invoke(): Result

    public sealed interface Result {
        public data object Success : Result
        public data class Failure(val error: CoreFailure) : Result
    }
}

internal class SyncServicesUseCaseImpl internal constructor(
    private val teamRepository: TeamRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider
) : SyncServicesUseCase {

    override suspend fun invoke(): SyncServicesUseCase.Result =
        (selfTeamIdProvider().getOrNull()?.let { teamId ->
            teamRepository.syncServices(teamId = teamId)
        } ?: Either.Right(Unit)).fold(
            { SyncServicesUseCase.Result.Failure(it) },
            { SyncServicesUseCase.Result.Success }
        )
}
