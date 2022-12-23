package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * This use case will observe and return the current session.
 * fixme: rename to ObserveCurrentSessionUseCase
 * @see [CurrentSessionResult.Success.accountInfo]
 */
class CurrentSessionFlowUseCase(private val sessionRepository: SessionRepository) {
    operator fun invoke(): Flow<CurrentSessionResult> =
        sessionRepository.currentSessionFlow().map {
            it.fold({
                when (it) {
                    StorageFailure.DataNotFound -> CurrentSessionResult.Failure.SessionNotFound
                    is StorageFailure.Generic -> CurrentSessionResult.Failure.Generic(it)
                }
            }, {
                CurrentSessionResult.Success(it)
            })
        }
}
