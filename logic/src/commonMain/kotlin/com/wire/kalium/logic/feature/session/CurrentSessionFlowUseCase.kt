package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CurrentSessionFlowUseCase(private val sessionRepository: SessionRepository) {
    operator fun invoke(): Flow<CurrentSessionResult> =
        sessionRepository.currentSessionFlow().map {
            it.fold({
                when (it) {
                    StorageFailure.DataNotFound -> CurrentSessionResult.Failure.SessionNotFound
                    is StorageFailure.Generic -> CurrentSessionResult.Failure.Generic(it)
                }
            }, { authSession ->
                CurrentSessionResult.Success(authSession)
            })
        }
}
