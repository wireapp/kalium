package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

interface PersistPersistentWebSocketConnectionStatusUseCase {
    suspend operator fun invoke(enabled: Boolean)
}

internal class PersistPersistentWebSocketConnectionStatusUseCaseImpl(
    private val userId: UserId,
    private val sessionRepository: SessionRepository
) : PersistPersistentWebSocketConnectionStatusUseCase {
    override suspend operator fun invoke(enabled: Boolean) =
        sessionRepository.updatePersistentWebSocketStatus(userId, enabled).fold({
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.LOCAL_STORAGE).e(
                "DataNotFound when persisting web socket status  : $it"
            )
        }, {
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.LOCAL_STORAGE).d(
                "Persistent WebSocket Connection Status Persisted successfully"
            )
        })

}
