package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.fold

sealed class CurrentSessionResult {
    data class Success(val authSession: AuthSession) : CurrentSessionResult()

    sealed class Failure : CurrentSessionResult() {
        object SessionNotFound : Failure()
        class Generic(coreFailure: CoreFailure) : Failure()
    }
}

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
