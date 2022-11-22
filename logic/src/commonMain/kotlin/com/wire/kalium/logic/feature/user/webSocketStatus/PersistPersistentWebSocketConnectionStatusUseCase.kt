package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

interface PersistPersistentWebSocketConnectionStatusUseCase {
    suspend operator fun invoke(enabled: Boolean)
}

internal class PersistPersistentWebSocketConnectionStatusUseCaseImpl(
    private val sessionRepository: SessionRepository
) : PersistPersistentWebSocketConnectionStatusUseCase {
    override suspend operator fun invoke(enabled: Boolean) {
        sessionRepository.currentSession().fold({
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.LOCAL_STORAGE).e(
                "DataNotFound when persisting web socket status  : $it"
            )
        }, {
            sessionRepository.updatePersistentWebSocketStatus(it.userId, enabled)

        })
    }

}

