package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfigUtil
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.network.api.configuration.EndPoints
import com.wire.kalium.network.api.configuration.ServerConfigResponse
import kotlinx.coroutines.flow.Flow

class ObserveServerConfigUseCase internal constructor(
    private val serverConfigRepository: ServerConfigRepository,
    private val serverConfigUtil: ServerConfigUtil
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
                PRODUCTION.also { config ->
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


    private companion object {
        // TODO: get the config from build json
        val PRODUCTION = ServerConfigResponse(
            EndPoints(
                apiBaseUrl = """https://prod-nginz-https.wire.com""",
                accountsBaseUrl = """https://account.wire.com""",
                webSocketBaseUrl = """https://prod-nginz-ssl.wire.com""",
                teamsUrl = """https://teams.wire.com""",
                blackListUrl = """https://clientblacklist.wire.com/prod""",
                websiteUrl = """https://wire.com""",
            ),
            title = "Production"
        )
        val STAGING = ServerConfigResponse(
            EndPoints(
                apiBaseUrl = """https://staging-nginz-https.zinfra.io""",
                accountsBaseUrl = """https://wire-account-staging.zinfra.io""",
                webSocketBaseUrl = """https://staging-nginz-ssl.zinfra.io""",
                teamsUrl = """https://wire-teams-staging.zinfra.io""",
                blackListUrl = """https://clientblacklist.wire.com/staging""",
                websiteUrl = """https://wire.com"""
            ),
            title = "Staging",
        )
        val DEFAULT = PRODUCTION
    }
}




