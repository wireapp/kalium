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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.session.SessionRepository

/**
 * This use case will return all sessions, including those that are invalid
 * (e.g. soft-logged-out accounts). Useful for displaying sessions on a login
 * screen where re-authentication is possible.
 *
 * @see [GetAllSessionsResult.Success.sessions]
 */
public class GetAllSessionsUseCase internal constructor(
    private val sessionRepository: SessionRepository
) {
    public suspend operator fun invoke(): GetAllSessionsResult = sessionRepository.allSessions().fold(
        {
            when (it) {
                StorageFailure.DataNotFound -> GetAllSessionsResult.Failure.NoSessionFound
                is StorageFailure.Generic -> GetAllSessionsResult.Failure.Generic(it)
            }
        },
        {
            GetAllSessionsResult.Success(it)
        }
    )
}
