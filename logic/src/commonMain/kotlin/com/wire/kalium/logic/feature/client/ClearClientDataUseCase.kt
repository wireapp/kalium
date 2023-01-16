package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapCryptoRequest
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for clearing the client data.
 * The proteus client will be cleared and the MLS client will be cleared.
 */
interface ClearClientDataUseCase {
    suspend operator fun invoke()
}

internal class ClearClientDataUseCaseImpl internal constructor(
    private val mlsClientProvider: MLSClientProvider,
    private val proteusClientProvider: ProteusClientProvider,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ClearClientDataUseCase {

    override suspend operator fun invoke() {
        withContext(dispatcher.default) {
            clearCrypto()
                .onSuccess { success ->
                    if (!success) {
                        kaliumLogger.e("Did not clear crypto storage")
                    }
                }
                .onFailure {
                    kaliumLogger.e("Error clearing crypto storage: $it")
                }
        }
    }

    private suspend fun clearCrypto(): Either<CoreFailure, Boolean> =
        wrapCryptoRequest {
            proteusClientProvider.clearLocalFiles()
        }.flatMap {
            mlsClientProvider.getMLSClient()
                .flatMap { mlsClient ->
                    wrapMLSRequest {
                        mlsClient.clearLocalFiles()
                    }
                }
        }
}
