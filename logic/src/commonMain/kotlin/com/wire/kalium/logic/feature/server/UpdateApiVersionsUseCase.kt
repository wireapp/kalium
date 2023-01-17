package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Iterates over all locally stored server configs and update each api version
 */
interface UpdateApiVersionsUseCase {
    suspend operator fun invoke()
}

class UpdateApiVersionsUseCaseImpl internal constructor(
    private val configRepository: ServerConfigRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : UpdateApiVersionsUseCase {
    override suspend operator fun invoke() {
        withContext(dispatcher.default) {
            configRepository.configList().onSuccess { configList ->
                configList.forEach {
                    configRepository.updateConfigApiVersion(it.id)
                }
            }
        }
    }
}
