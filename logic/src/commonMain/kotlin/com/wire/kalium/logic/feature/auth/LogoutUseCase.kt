package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.di.UserSessionScopeProvider
import com.wire.kalium.logic.di.UserSessionScopeProviderImpl
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

// TODO(testing): This class is a pain to test because of AuthenticatedDataSourceSet
class LogoutUseCase @Suppress("LongParameterList") internal constructor(
    private val logoutRepository: LogoutRepository,
    private val sessionRepository: SessionRepository,
    private val userId: QualifiedID,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    private val clientRepository: ClientRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val deregisterTokenUseCase: DeregisterTokenUseCase,
    private val userSessionScopeProvider: UserSessionScopeProvider = UserSessionScopeProviderImpl
) {

    // TODO(refactor): Maybe we can simplify by taking some of the responsibility away from here.
    //                 Perhaps [UserSessionScope] (or another specialised class) can observe
    //                 the [LogoutRepository.observeLogout] and invalidating everything in [CoreLogic] level.
    suspend operator fun invoke(reason: LogoutReason = LogoutReason.USER_INTENTION) {
        deregisterTokenUseCase()
        logoutRepository.logout()
        logoutRepository.onLogout(reason)
        clearCrypto()
        clearUserStorage()
        clearUserSessionAndUpdateCurrent()
        clearInMemoryUserSession()
    }

    private fun clearInMemoryUserSession() {
        userSessionScopeProvider.delete(userId)
    }

    private fun clearUserSessionAndUpdateCurrent() {
        sessionRepository.deleteSession(userId)
        sessionRepository.allSessions().onSuccess {
            sessionRepository.updateCurrentSession(it.first().tokens.userId)
        }
    }

    private fun clearUserStorage() {
        authenticatedDataSourceSet.userDatabaseProvider.nuke()
        authenticatedDataSourceSet.kaliumPreferencesSettings.nuke()
    }

    private suspend fun clearCrypto() {
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
