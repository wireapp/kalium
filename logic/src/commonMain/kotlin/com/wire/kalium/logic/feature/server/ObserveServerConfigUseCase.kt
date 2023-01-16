package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Observes for changes and returns the list of [ServerConfig] stored locally.
 */
class ObserveServerConfigUseCase internal constructor(
    private val serverConfigRepository: ServerConfigRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    sealed class Result {
        data class Success(val value: Flow<List<ServerConfig>>) : Result()
        sealed class Failure : Result() {
            class Generic(val genericFailure: CoreFailure) : Failure()
        }
    }

    /**
     * @return the [Result] with the [Flow] list of [ServerConfig] if successful, otherwise a mapped failure.
     */
    suspend operator fun invoke(): Result = withContext(dispatchers.default) {
        serverConfigRepository.configList().map { configList ->
            configList.isNullOrEmpty()
        }.onSuccess { isEmpty ->
            if (isEmpty) {
                // TODO: store all of the configs from the build json file
                ServerConfig.DEFAULT.also { config ->
                    // TODO: what do do if one of the insert failed
                    serverConfigRepository.fetchApiVersionAndStore(config).onFailure {
                        handleError(it)
                    }
                }
            }
        }

        serverConfigRepository.configFlow().fold({
            Result.Failure.Generic(it)
        }, {
            Result.Success(it)
        })
    }

    private fun handleError(coreFailure: CoreFailure): Result.Failure {
        return Result.Failure.Generic(coreFailure)
    }
}
