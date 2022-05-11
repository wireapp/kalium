package com.wire.kalium.logic.feature.server_config

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfigUtil
import com.wire.kalium.logic.configuration.server.ServerConfigUtilImpl
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold

class GetServerConfigUseCase internal constructor(
    private val configRepository: ServerConfigRepository,
    private val serverConfigUtil: ServerConfigUtil = ServerConfigUtilImpl
) {
    suspend operator fun invoke(url: String): GetServerConfigResult {
        val serverConfigDTO = configRepository.fetchRemoteConfig(url).let {
            when (it) {
                is Either.Right -> it.value
                is Either.Left -> return handleError(it.value)
            }
        }
        val versionInfoDTO = configRepository.fetchRemoteApiVersion(serverConfigDTO).let {
            when (it) {
                is Either.Right -> it.value
                is Either.Left -> return handleError(it.value)
            }
        }

        val commonApiVersion = serverConfigUtil.calculateApiVersion(versionInfoDTO.supported).let {
            when (it) {
                is Either.Right -> it.value
                is Either.Left -> return handleError(it.value)
            }
        }

        return configRepository.storeConfig(serverConfigDTO, versionInfoDTO.domain, commonApiVersion, versionInfoDTO.federation)
            .fold({
                handleError(it)
            }, {
                GetServerConfigResult.Success(it)
            })
    }


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
        object UnknownServerVersion: Failure()
        object TooNewVersion: Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}


