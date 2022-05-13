package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold

class GetServerConfigUseCase internal constructor(
    private val configRepository: ServerConfigRepository
) {
    suspend operator fun invoke(url: String, senderId: String? = null): GetServerConfigResult =
        configRepository.fetchRemoteConfig(url)
            .flatMap { serverConfigDTO ->
                configRepository.storeConfig(serverConfigDTO, senderId)
            }.fold({
                GetServerConfigResult.Failure.Generic(it)
            }, {
                GetServerConfigResult.Success(it)
            })

}
