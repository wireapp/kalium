package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.kaliumLogger

interface ClearUserDataUseCase {
    suspend operator fun invoke()
}

class ClearUserDataUseCaseImpl @Suppress("LongParameterList") constructor(
    private val userId: QualifiedID,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    private val clientRepository: ClientRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val userSessionScopeProvider: UserSessionScopeProvider
) : ClearUserDataUseCase {

    override suspend operator fun invoke() {
        clearCrypto()
        clearUserStorage()
        clearInMemoryUserSession()
    }

    private fun clearInMemoryUserSession() {
        userSessionScopeProvider.delete(userId)
    }

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
