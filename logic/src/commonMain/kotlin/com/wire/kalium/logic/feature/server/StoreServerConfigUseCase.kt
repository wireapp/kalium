package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.fold

fun interface StoreServerConfigUseCase {
    suspend operator fun invoke(links: ServerConfig.Links, versionInfo: ServerConfig.VersionInfo): StoreServerConfigResult
}

internal class StoreServerConfigUseCaseImpl(
    private val configRepository: ServerConfigRepository
) : StoreServerConfigUseCase {

    override suspend fun invoke(links: ServerConfig.Links, versionInfo: ServerConfig.VersionInfo): StoreServerConfigResult =
        configRepository.storeConfig(links, versionInfo)
            .fold({ StoreServerConfigResult.Failure.Generic(it) }, { StoreServerConfigResult.Success(it) })
}

sealed class StoreServerConfigResult {
    // TODO: change to return the id only so we are now passing the whole config object around in the app
    class Success(val serverConfig: ServerConfig) : StoreServerConfigResult()

    sealed class Failure : StoreServerConfigResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
