package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will return all valid sessions.
 *
 * @see [GetAllSessionsResult.Success.sessions]
 */
class GetSessionsUseCase(
    private val sessionRepository: SessionRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    suspend operator fun invoke(): GetAllSessionsResult =
        withContext(dispatchers.default) {
            sessionRepository.allValidSessions().fold(
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
}
