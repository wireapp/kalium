package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.auth.PersistentWebSocketStatus
import kotlinx.coroutines.flow.Flow

interface ObservePersistentWebSocketConnectionStatusUseCase {
    suspend operator fun invoke(): Flow<List<PersistentWebSocketStatus>>
}

internal class ObservePersistentWebSocketConnectionStatusUseCaseImpl(
    private val sessionRepository: SessionRepository
) : ObservePersistentWebSocketConnectionStatusUseCase {
    override suspend operator fun invoke(): Flow<List<PersistentWebSocketStatus>> =
        sessionRepository.getAllValidAccountPersistentWebSocketStatus()
}
