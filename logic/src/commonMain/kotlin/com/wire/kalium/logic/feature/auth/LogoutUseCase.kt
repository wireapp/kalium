package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.onSuccess

class LogoutUseCase(
    private val logoutRepository: LogoutRepository,
    private val sessionRepository: SessionRepository,
    private val authSession: AuthSession,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet
) {
    suspend operator fun invoke() {
        // TODO: async for the network call
        // TODO: clear crypto session ?
        logoutRepository.logout()
        authenticatedDataSourceSet.database.nuke()
        authenticatedDataSourceSet.kaliumPreferencesSettings.nuke()
        sessionRepository.deleteSession(authSession.userId)
        sessionRepository.getSessions().onSuccess {
            sessionRepository.updateCurrentSession(it.first().userId)
        }
    }
}
