package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.fold

/**
 * Stores the server configuration metadata, like main urls and flags for this server.
 */
fun interface StoreServerConfigUseCase {
    /**
     * @param links the server configuration links to store @see [ServerConfig.Links]
     * @param versionInfo the server configuration version to store @see [ServerConfig.VersionInfo]
     * @return the [StoreServerConfigResult] whether the operation was successful or not and the stored [ServerConfig]
     */
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
