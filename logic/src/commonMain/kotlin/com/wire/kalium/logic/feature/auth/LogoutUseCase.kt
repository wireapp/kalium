package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.di.AuthenticatedDataSourceSetProvider
import com.wire.kalium.logic.di.AuthenticatedDataSourceSetProviderImpl
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

class LogoutUseCase(
    private val logoutRepository: LogoutRepository,
    private val sessionRepository: SessionRepository,
    private val userId: QualifiedID,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    private val clientRepository: ClientRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val authenticatedDataSourceSetProvider: AuthenticatedDataSourceSetProvider = AuthenticatedDataSourceSetProviderImpl
) {
    suspend operator fun invoke() {
        //TODO deregister push notification token
        logoutRepository.logout()
        clearCrypto()
        clearUserStorage()
        clearUserSessionAndUpdateCurrent()
        clearInMemoryUserSession()
    }

    private fun clearInMemoryUserSession() {
        authenticatedDataSourceSetProvider.delete(userId)
    }

    private fun clearUserSessionAndUpdateCurrent() {
        sessionRepository.deleteSession(userId)
        sessionRepository.allSessions().onSuccess {
            sessionRepository.updateCurrentSession(it.first().userId)
        }
    }

    private fun clearUserStorage() {
        authenticatedDataSourceSet.userDatabaseProvider.nuke()
        authenticatedDataSourceSet.kaliumPreferencesSettings.nuke()
    }

    private fun clearCrypto() {
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
