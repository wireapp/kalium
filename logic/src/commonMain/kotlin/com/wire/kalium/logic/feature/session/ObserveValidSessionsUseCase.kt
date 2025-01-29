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

package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * This use case will return [Flow] of all valid sessions.
 *
 * @see [GetAllSessionsResult.Success.sessions]
 */
class ObserveValidSessionsUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(): Flow<GetAllSessionsResult> = sessionRepository.allValidSessionsFlow()
        .map {
            it.fold(
                { failure ->
                    when (failure) {
                        StorageFailure.DataNotFound -> GetAllSessionsResult.Failure.NoSessionFound
                        is StorageFailure.Generic -> GetAllSessionsResult.Failure.Generic(failure)
                    }
                }, { sessions ->
                    GetAllSessionsResult.Success(sessions)
                }
            )
        }
}
