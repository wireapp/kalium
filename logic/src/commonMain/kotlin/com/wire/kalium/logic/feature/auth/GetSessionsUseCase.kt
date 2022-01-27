package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.failure.SessionFailure
import com.wire.kalium.logic.feature.session.GetAllSessionsResult
import com.wire.kalium.logic.functional.Either

class GetSessionsUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(): GetAllSessionsResult =
        when (val result = sessionRepository.getSessions()) {
            is Either.Left -> {
                if (result.value is SessionFailure.NoSessionFound) {
                    GetAllSessionsResult.Failure.NoSessionFound
                } else GetAllSessionsResult.Failure.Generic(result.value)
            }
            is Either.Right -> GetAllSessionsResult.Success(result.value)
        }


}
