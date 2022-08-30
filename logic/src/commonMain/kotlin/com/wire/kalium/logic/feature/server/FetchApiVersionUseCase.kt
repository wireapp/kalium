package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.fold

interface FetchApiVersionUseCase {
    suspend operator fun invoke(serverLinks: ServerConfig.Links): FetchApiVersionResult
}

class FetchApiVersionUseCaseImpl internal constructor(
    private val configRepository: ServerConfigRepository
) : FetchApiVersionUseCase {
    override suspend operator fun invoke(serverLinks: ServerConfig.Links): FetchApiVersionResult =
        configRepository.fetchApiVersionAndStore(serverLinks)
            .fold(
                { handleError(it) },
                { FetchApiVersionResult.Success(it) }
            )

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
