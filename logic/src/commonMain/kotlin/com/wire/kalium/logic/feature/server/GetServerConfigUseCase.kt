package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Gets the [ServerConfig.Links] stored locally, using the url as a key.
 */
class GetServerConfigUseCase internal constructor(
    private val configRepository: ServerConfigRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    /**
     * @param url the url to use as a key to get the [ServerConfig.Links]
     * @return the [Result] with the [ServerConfig.Links] if successful, otherwise a mapped failure.
     */
    suspend operator fun invoke(url: String): GetServerConfigResult = withContext(dispatchers.default) {
        configRepository.fetchRemoteConfig(url).fold({
            GetServerConfigResult.Failure.Generic(it)
        }, { GetServerConfigResult.Success(it) })
    }
}

sealed class GetServerConfigResult {
    // TODO: change to return the id only so we are now passing the whole config object around in the app
    class Success(val serverConfigLinks: ServerConfig.Links) : GetServerConfigResult()

    sealed class Failure : GetServerConfigResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
