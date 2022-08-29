package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

interface LogoutUseCase {
    suspend operator fun invoke(reason: LogoutReason = LogoutReason.SELF_LOGOUT)
}

class LogoutUseCaseImpl @Suppress("LongParameterList") constructor(
    private val logoutRepository: LogoutRepository,
    private val sessionRepository: SessionRepository,
    private val userId: QualifiedID,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    private val clientRepository: ClientRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val deregisterTokenUseCase: DeregisterTokenUseCase,
    private val userSessionScopeProvider: UserSessionScopeProvider
) : LogoutUseCase {
    // TODO(refactor): Maybe we can simplify by taking some of the responsibility away from here.
    //                 Perhaps [UserSessionScope] (or another specialised class) can observe
    //                 the [LogoutRepository.observeLogout] and invalidating everything in [CoreLogic] level.
    override suspend operator fun invoke(reason: LogoutReason) {
        deregisterTokenUseCase()
        logoutRepository.logout()
        logout(reason)
        clearCrypto()
        if (isHardLogout(reason)) {
            clearUserStorage()
        }
        updateCurrentSession()
        clearInMemoryUserSession()
    }

    private fun isHardLogout(reason: LogoutReason) = when (reason) {
        LogoutReason.SELF_LOGOUT -> true
        LogoutReason.REMOVED_CLIENT -> false
        LogoutReason.DELETED_ACCOUNT -> false
        LogoutReason.SESSION_EXPIRED -> false
    }

    private fun clearInMemoryUserSession() {
        userSessionScopeProvider.delete(userId)
    }

    private fun updateCurrentSession() {
        sessionRepository.allSessions().onSuccess {
            sessionRepository.updateCurrentSession(it.first().session.userId)
        }
    }

    private fun logout(reason: LogoutReason) =
        sessionRepository.logout(userId = userId, reason, isHardLogout(reason))

    private fun clearUserStorage() {
        authenticatedDataSourceSet.userDatabaseProvider.nuke()
        // exclude clientId clear from this step
        authenticatedDataSourceSet.kaliumPreferencesSettings.nuke()
    }

    private suspend fun clearCrypto() {
        // clear clientId here
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
