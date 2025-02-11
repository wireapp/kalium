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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.common.functional.fold

sealed class CurrentSessionResult {
    data class Success(val accountInfo: AccountInfo) : CurrentSessionResult()

    sealed class Failure : CurrentSessionResult() {
        data object SessionNotFound : Failure()

        @Suppress("UNUSED_PARAMETER") // It's used by consumers of Kalium
        class Generic(coreFailure: CoreFailure) : Failure()
    }
}

/**
 * This use case will return the current session.
 * @see [CurrentSessionResult.Success.accountInfo]
 */
class CurrentSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(): CurrentSessionResult =
        sessionRepository.currentSession().fold({
            when (it) {
                StorageFailure.DataNotFound -> CurrentSessionResult.Failure.SessionNotFound
                is StorageFailure.Generic -> CurrentSessionResult.Failure.Generic(it)
            }
        }, { authSession ->
            CurrentSessionResult.Success(authSession)
        })
}
