package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.auth.GetSessionsUseCase

class SessionScope(
    private val sessionRepository: SessionRepository
) {
    val allSessions get() = GetSessionsUseCase(sessionRepository)
    val saveSession get() = SaveSessionUseCase(sessionRepository)
}
