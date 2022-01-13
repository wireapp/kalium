package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase

class SessionScope(
    sessionRepository: SessionRepository
) {
    val allSessions = GetAllSessionsUseCase(sessionRepository)
    val saveSession = SaveSessionUseCase(sessionRepository)
}
