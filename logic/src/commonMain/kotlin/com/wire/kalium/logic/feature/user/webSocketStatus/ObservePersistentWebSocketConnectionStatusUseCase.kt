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

package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.auth.PersistentWebSocketStatus
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow

/**
 * Observes the persistent web socket connection configuration status, for all accounts.
 */
interface ObservePersistentWebSocketConnectionStatusUseCase {
    /**
     * @return [Result] containing the [Flow] of [PersistentWebSocketStatus] if successful, otherwise a mapped failure.
     */
    suspend operator fun invoke(): Result

    sealed class Result {
        class Success(val persistentWebSocketStatusListFlow: Flow<List<PersistentWebSocketStatus>>) : Result()
        sealed class Failure : Result() {
            data object StorageFailure : Failure()
            data class Generic(val genericFailure: CoreFailure) : Failure()
        }
    }
}

internal class ObservePersistentWebSocketConnectionStatusUseCaseImpl(
    private val sessionRepository: SessionRepository
) : ObservePersistentWebSocketConnectionStatusUseCase {
    override suspend operator fun invoke(): ObservePersistentWebSocketConnectionStatusUseCase.Result =
        sessionRepository.getAllValidAccountPersistentWebSocketStatus().fold({
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SYNC)
                .i("Error while fetching valid accounts persistent web socket status ")
            ObservePersistentWebSocketConnectionStatusUseCase.Result.Failure.StorageFailure

        }, {
            ObservePersistentWebSocketConnectionStatusUseCase.Result.Success(it)
        })
}
