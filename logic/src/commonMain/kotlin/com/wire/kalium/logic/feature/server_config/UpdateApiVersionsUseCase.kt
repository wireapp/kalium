package com.wire.kalium.logic.feature.server_config

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.onSuccess

/**
 * Iterates over all locally stored server configs and update each api version
 */
interface UpdateApiVersionsUseCase {
    suspend operator fun invoke()
}

class UpdateApiVersionsUseCaseImpl internal constructor(
    private val configRepository: ServerConfigRepository
) : UpdateApiVersionsUseCase {
    override suspend operator fun invoke() {
        configRepository.configList().onSuccess { configList ->
            configList.forEach {
                configRepository.updateConfigApiVersion(it.id)
            }
        }
    }
}

