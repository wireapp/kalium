package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.failure.SessionFailure
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

sealed class CurrentSessionResult {
    data class Success(val authSession: AuthSession) : CurrentSessionResult()

    sealed class Failure : CurrentSessionResult() {
        object SessionNotFound : Failure()
        class Generic(val sessionFailure: SessionFailure) : Failure()
    }
}

class CurrentSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(): CurrentSessionResult = suspending {
        sessionRepository.currentSession()
    }.fold({ sessionFailure ->
        when (sessionFailure) {
            is SessionFailure.NoSessionFound -> CurrentSessionResult.Failure.SessionNotFound
        }
    }, { authSession ->
        CurrentSessionResult.Success(authSession)
    })
}
