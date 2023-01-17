package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

interface GetPersistentWebSocketStatus {
    suspend operator fun invoke(): Boolean
}

internal class GetPersistentWebSocketStatusImpl(
    private val userId: UserId,
    private val sessionRepository: SessionRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : GetPersistentWebSocketStatus {
    override suspend operator fun invoke(): Boolean = withContext(dispatchers.default) {
        sessionRepository.persistentWebSocketStatus(userId).fold({
            false
        }, {
            it
        })
    }
}
