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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.user.LegalHoldStatus
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import io.mockative.Mockable

/**
 * Use case that allows to fetch and persist the legal hold state for the self user.
 */
@Mockable
interface FetchLegalHoldForSelfUserFromRemoteUseCase {
    suspend operator fun invoke(): Either<CoreFailure, LegalHoldStatus>
}

class FetchLegalHoldForSelfUserFromRemoteUseCaseImpl internal constructor(
    private val teamRepository: TeamRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
) : FetchLegalHoldForSelfUserFromRemoteUseCase {

    override suspend fun invoke(): Either<CoreFailure, LegalHoldStatus> =
        selfTeamIdProvider()
            .flatMap { teamId ->
                if (teamId == null) Either.Right(LegalHoldStatus.NO_CONSENT)
                else teamRepository.fetchLegalHoldStatus(teamId)
            }
}
