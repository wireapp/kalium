package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import kotlinx.coroutines.flow.Flow

class ObserveServerConfigUseCase internal constructor(
    private val serverConfigRepository: ServerConfigRepository
) {
    sealed class Result {
        data class Success(val value: Flow<List<ServerConfig>>) : Result()
        sealed class Failure : Result() {
            class Generic(val genericFailure: CoreFailure) : Failure()
        }
    }


    suspend operator fun invoke(): Result {
        serverConfigRepository.configList().map { configList ->
            configList.isNullOrEmpty()
        }.onSuccess { isEmpty ->
            if (isEmpty) {
                // TODO: store all of the configs from the build json file
                ServerConfig.DEFAULT.also { config ->
                    // TODO: what do do if one of the insert failed
                    serverConfigRepository.fetchApiVersionAndStore(config).onFailure {
                        return handleError(it)
                    }
                }
            }
        }

        return serverConfigRepository.configFlow().fold({
            Result.Failure.Generic(it)
        }, {
            Result.Success(it)
        })
    }
    private fun handleError(coreFailure: CoreFailure): Result.Failure {
        return Result.Failure.Generic(coreFailure)
    }
}




