package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.fold

/**
 * This use case will return all valid sessions.
 *
 * @see [GetAllSessionsResult.Success.sessions]
 */
class GetSessionsUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(): GetAllSessionsResult = sessionRepository.allValidSessions().fold(
        {
            when (it) {
                StorageFailure.DataNotFound -> GetAllSessionsResult.Failure.NoSessionFound
                is StorageFailure.Generic -> GetAllSessionsResult.Failure.Generic(it)
            }
        }, {
            GetAllSessionsResult.Success(it)
        }
    )
}
