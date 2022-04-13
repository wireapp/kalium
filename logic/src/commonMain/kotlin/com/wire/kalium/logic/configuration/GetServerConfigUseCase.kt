package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.functional.flatMap

class GetServerConfigUseCase internal constructor(
    private val configRepository: ServerConfigRepository
) {
    suspend operator fun invoke(url: String): GetServerConfigResult = configRepository.fetchRemoteConfig(url).flatMap { serverConfigDTO ->
        configRepository.storeConfig(serverConfigDTO)
    }.fold({
        GetServerConfigResult.Failure.Generic(it)
    }, {
        GetServerConfigResult.Success(it)
    })

}
