package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.di.UserSessionScopeProvider
import com.wire.kalium.logic.di.UserSessionScopeProviderImpl
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

interface LogoutUseCase {
    suspend operator fun invoke(isHardLogout: Boolean = false)
}

class LogoutUseCaseImpl @Suppress("LongParameterList") constructor(
    private val logoutRepository: LogoutRepository,
    private val sessionRepository: SessionRepository,
    private val userId: QualifiedID,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    private val clientRepository: ClientRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val deregisterTokenUseCase: DeregisterTokenUseCase,
    private val userSessionScopeProvider: UserSessionScopeProvider = UserSessionScopeProviderImpl
) : LogoutUseCase {
    override suspend operator fun invoke(isHardLogout: Boolean) {
        deregisterTokenUseCase()
        logoutRepository.logout()
        clearCrypto()
        if (isHardLogout) {
            clearUserStorage()
        }
        clearUserSessionAndUpdateCurrent()
        clearInMemoryUserSession()
    }

    private fun clearInMemoryUserSession() {
        userSessionScopeProvider.delete(userId)
    }

    private suspend fun deregisterNativePushToken() {
        deregisterTokenUseCase()
    }

    private fun clearUserSessionAndUpdateCurrent() {
        sessionRepository.deleteSession(userId)
        sessionRepository.allSessions().onSuccess {
            sessionRepository.updateCurrentSession(it.first().session.userId)
        }
    }

    private fun clearUserStorage() {
        authenticatedDataSourceSet.userDatabaseProvider.nuke()
        //exclude clientId clear from this step
        authenticatedDataSourceSet.kaliumPreferencesSettings.nuke()
    }

    private suspend fun clearCrypto() {
        //clear clientId here
        authenticatedDataSourceSet.proteusClient.clearLocalFiles()

        clientRepository.currentClientId().let { clientID ->
            if (clientID.isLeft()) {
                kaliumLogger.e("unable to access account Client ID")
                return
            }
            mlsClientProvider.getMLSClient(clientID.value).let { mlsClient ->
                if (mlsClient.isLeft()) {
                    kaliumLogger.e("unable to access account MLS client ID")
                    return
                } else {
                    mlsClient.value.clearLocalFiles()
                }
            }
        }
    }
}
