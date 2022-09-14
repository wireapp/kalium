package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.kaliumLogger

interface ClearClientDataUseCase {
    suspend operator fun invoke()
}

internal class ClearClientDataUseCaseImpl internal constructor(
    private val clientRepository: ClientRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val proteusClient: ProteusClient
) : ClearClientDataUseCase {

    override suspend operator fun invoke() {
        clearCrypto()
    }

    private suspend fun clearCrypto() {
        // clear clientId here
        proteusClient.clearLocalFiles()

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
