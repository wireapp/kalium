package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map

class NumberOfAuthenticatedAccountsUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(): Result = sessionRepository.allSessions().map { allSessions ->
        allSessions.count { it.session is AuthSession.Session.Valid }
    }.fold({ Result.Failure(it) }, { Result.Success(it) })

    sealed class Result {
        data class Success(val count: Int) : Result()
        data class Failure(val cause: StorageFailure) : Result()
    }
}
