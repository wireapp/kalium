package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.auth.AuthSession

@Deprecated("unsafe API", replaceWith = ReplaceWith("com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase", "com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase"))
class SaveSessionUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(authSession: AuthSession) {
        sessionRepository.storeSession(authSession)
    }
}
