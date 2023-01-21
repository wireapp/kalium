package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.auth.PersistentWebSocketStatus
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow

/**
 * Observes the persistent web socket connection configuration status, for all accounts.
 */
interface ObservePersistentWebSocketConnectionStatusUseCase {
    /**
     * @return [Result] containing the [Flow] of [PersistentWebSocketStatus] if successful, otherwise a mapped failure.
     */
    suspend operator fun invoke(): Result

    sealed class Result {
        class Success(val persistentWebSocketStatusListFlow: Flow<List<PersistentWebSocketStatus>>) : Result()
        sealed class Failure : Result() {
            object StorageFailure : Failure()
            class Generic(val genericFailure: CoreFailure) : Failure()
        }
    }
}

internal class ObservePersistentWebSocketConnectionStatusUseCaseImpl(
    private val sessionRepository: SessionRepository
) : ObservePersistentWebSocketConnectionStatusUseCase {
    override suspend operator fun invoke(): ObservePersistentWebSocketConnectionStatusUseCase.Result =
        sessionRepository.getAllValidAccountPersistentWebSocketStatus().fold({
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SYNC)
                .i("Error while fetching valid accounts persistent web socket status ")
            ObservePersistentWebSocketConnectionStatusUseCase.Result.Failure.StorageFailure

        }, {
            ObservePersistentWebSocketConnectionStatusUseCase.Result.Success(it)
        })
}
