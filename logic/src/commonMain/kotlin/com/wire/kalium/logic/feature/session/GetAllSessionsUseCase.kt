package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.failure.SessionFailure
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class GetAllSessionsUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(): Flow<GetAllSessionsResult> = flow {
        sessionRepository.getSessions().map {
            when (it) {
                is Either.Left -> {
                    if (it.value is SessionFailure.NoSessionFound) {
                        emit(GetAllSessionsResult.Failure.NoSessionFound)
                    } else emit(GetAllSessionsResult.Failure.Generic(it.value))
                }
                is Either.Right -> emit(GetAllSessionsResult.Success(it.value))
            }
        }
    }
}
