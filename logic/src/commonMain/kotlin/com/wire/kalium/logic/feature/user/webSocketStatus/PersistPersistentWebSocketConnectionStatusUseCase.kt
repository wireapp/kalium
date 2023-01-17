package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for persisting the persistent web socket connection status of the current user.
 */
interface PersistPersistentWebSocketConnectionStatusUseCase {
    /**
     * @param enabled true if the persistent web socket connection should be enabled, false otherwise
     */
    suspend operator fun invoke(enabled: Boolean)
}

internal class PersistPersistentWebSocketConnectionStatusUseCaseImpl(
    private val userId: UserId,
    private val sessionRepository: SessionRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : PersistPersistentWebSocketConnectionStatusUseCase {
    override suspend operator fun invoke(enabled: Boolean) =
        withContext(dispatchers.default) {
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
}
