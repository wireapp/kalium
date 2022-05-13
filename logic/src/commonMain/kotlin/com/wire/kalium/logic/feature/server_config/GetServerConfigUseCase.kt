package com.wire.kalium.logic.feature.server_config

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold

class GetServerConfigUseCase internal constructor(
    private val configRepository: ServerConfigRepository
) {
    suspend operator fun invoke(url: String): GetServerConfigResult =
        configRepository.fetchRemoteConfig(url)
            .flatMap { configRepository.fetchApiVersionAndStore(it) }
            .fold(
                { handleError(it) },
                { GetServerConfigResult.Success(it) }
            )

    private fun handleError(coreFailure: CoreFailure): GetServerConfigResult.Failure =
        when (coreFailure) {
            is ServerConfigFailure.NewServerVersion -> GetServerConfigResult.Failure.TooNewVersion
            is ServerConfigFailure.UnknownServerVersion -> GetServerConfigResult.Failure.UnknownServerVersion
            else -> GetServerConfigResult.Failure.UnknownServerVersion
        }
}

sealed class GetServerConfigResult {
    // TODO: change to return the id only so we are now passing the whole config object around in the app
    class Success(val serverConfig: ServerConfig) : GetServerConfigResult()

    sealed class Failure : GetServerConfigResult() {
        object UnknownServerVersion : Failure()
        object TooNewVersion : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
