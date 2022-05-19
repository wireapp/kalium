package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository

class SessionScope(
    private val sessionRepository: SessionRepository
) {
    val allSessions get() = GetSessionsUseCase(sessionRepository)
    val saveSession get() = SaveSessionUseCase(sessionRepository)
    val currentSession get() = CurrentSessionUseCase(sessionRepository)
    val currentSessionFlow get() = CurrentSessionFlowUseCase(sessionRepository)
    val updateCurrentSession get() = UpdateCurrentSessionUseCase(sessionRepository)
}
