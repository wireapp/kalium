package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.auth.AuthSession

class SaveSessionUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(authSession: AuthSession) {
        sessionRepository.storeSession(authSession)
    }
}
