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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.fold

sealed class DoesValidSessionExistResult {
    data class Success(val doesValidSessionExist: Boolean) : DoesValidSessionExistResult()

    sealed class Failure : DoesValidSessionExistResult() {
        @Suppress("UNUSED_PARAMETER") // It's used by consumers of Kalium
        class Generic(coreFailure: CoreFailure) : Failure()
    }
}

/**
 * This use case will return the information whether the valid session exists for a given user id.
 */
class DoesValidSessionExistUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(userId: UserId): DoesValidSessionExistResult =
        sessionRepository.doesValidSessionExist(userId).fold({
            when (it) {
                StorageFailure.DataNotFound -> DoesValidSessionExistResult.Success(false)
                is StorageFailure.Generic -> DoesValidSessionExistResult.Failure.Generic(it)
            }
        }, {
            DoesValidSessionExistResult.Success(it)
        })
}
