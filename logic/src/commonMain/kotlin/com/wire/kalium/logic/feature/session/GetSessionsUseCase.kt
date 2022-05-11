package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.fold

class GetSessionsUseCase(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): GetAllSessionsResult = sessionRepository.allSessions().fold(
        {
            when (it) {
                StorageFailure.DataNotFound -> GetAllSessionsResult.Failure.NoSessionFound
                is StorageFailure.Generic -> GetAllSessionsResult.Failure.Generic(it)
            }
        }, {
            GetAllSessionsResult.Success(it)
        })
}
