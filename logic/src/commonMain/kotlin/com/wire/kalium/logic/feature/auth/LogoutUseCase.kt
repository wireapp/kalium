package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.onSuccess

class LogoutUseCase(
    private val logoutRepository: LogoutRepository,
    private val sessionRepository: SessionRepository,
    private val userId: QualifiedID,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet
) {
    suspend operator fun invoke() {
        // TODO: async for the network call
        // TODO: clear crypto files ?
        authenticatedDataSourceSet.proteusClient.close()
        authenticatedDataSourceSet.proteusClient.nuke()
        logoutRepository.logout()
        authenticatedDataSourceSet.userDatabaseProvider.nuke()
        authenticatedDataSourceSet.kaliumPreferencesSettings.nuke()
        sessionRepository.deleteSession(userId)
        sessionRepository.allSessions().onSuccess {
            sessionRepository.updateCurrentSession(it.first().userId)
        }
    }
}
