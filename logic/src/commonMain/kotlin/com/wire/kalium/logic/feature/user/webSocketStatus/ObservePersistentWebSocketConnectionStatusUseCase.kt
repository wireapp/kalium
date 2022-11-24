package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.auth.PersistentWebSocketStatus
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow

interface ObservePersistentWebSocketConnectionStatusUseCase {
    suspend operator fun invoke(): Result
}

internal class ObservePersistentWebSocketConnectionStatusUseCaseImpl(
    private val sessionRepository: SessionRepository
) : ObservePersistentWebSocketConnectionStatusUseCase {
    override suspend operator fun invoke(): Result = sessionRepository.getAllValidAccountPersistentWebSocketStatus().fold({
        kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SYNC)
            .i("Error while fetching valid accounts persistent web socket status ")
        Result.Failure.StorageFailure

    }, {
        Result.Success(it)
    })
}

sealed class Result {
    class Success(val persistentWebSocketStatusListFlow: Flow<List<PersistentWebSocketStatus>>) : Result()
    sealed class Failure : Result() {
        object StorageFailure : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
