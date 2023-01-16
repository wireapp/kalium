package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Fetches the server api version, for the given server backend.
 */
interface FetchApiVersionUseCase {
    /**
     * @param serverLinks the server backend links to fetch the api version from
     * @return the [FetchApiVersionResult] the server configuration version if successful, otherwise a mapped failure.
     */
    suspend operator fun invoke(serverLinks: ServerConfig.Links): FetchApiVersionResult
}

class FetchApiVersionUseCaseImpl internal constructor(
    private val configRepository: ServerConfigRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : FetchApiVersionUseCase {
    override suspend operator fun invoke(serverLinks: ServerConfig.Links): FetchApiVersionResult =
        withContext(dispatchers.default) {
            configRepository.fetchApiVersionAndStore(serverLinks)
                .fold(
                    { handleError(it) },
                    { FetchApiVersionResult.Success(it) }
                )
        }

    private fun handleError(coreFailure: CoreFailure): FetchApiVersionResult.Failure =
        when (coreFailure) {
            is ServerConfigFailure.NewServerVersion -> FetchApiVersionResult.Failure.TooNewVersion
            is ServerConfigFailure.UnknownServerVersion -> FetchApiVersionResult.Failure.UnknownServerVersion
            else -> FetchApiVersionResult.Failure.UnknownServerVersion
        }
}

sealed class FetchApiVersionResult {
    class Success(val serverConfig: ServerConfig) : FetchApiVersionResult()

    sealed class Failure : FetchApiVersionResult() {
        object UnknownServerVersion : Failure()
        object TooNewVersion : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
