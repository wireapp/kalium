package com.wire.kalium.logic.feature.auth.autoVersioningAuth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.CoreLogicCommon
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.functional.fold

class AutoVersionAuthScopeUseCase(
    private val serverLinks: ServerConfig.Links,
    private val coreLogic: CoreLogicCommon
) {
    suspend operator fun invoke(proxyCredentials: (() -> Pair<String, String>)?): Result =
        coreLogic.getGlobalScope().serverConfigRepository.getOrFetchMetadata(serverLinks).fold({
            handleError(it)
        }, { serverConfig ->
            Result.Success(coreLogic.getAuthenticationScope(serverConfig, proxyCredentials))
        })

    private fun handleError(coreFailure: CoreFailure): Result.Failure =
        when (coreFailure) {
            is ServerConfigFailure.NewServerVersion -> Result.Failure.TooNewVersion
            is ServerConfigFailure.UnknownServerVersion -> Result.Failure.UnknownServerVersion
            else -> Result.Failure.Generic(coreFailure)
        }

    sealed class Result {
        class Success(val authenticationScope: AuthenticationScope) : Result()

        sealed class Failure : Result() {
            object UnknownServerVersion : Failure()
            object TooNewVersion : Failure()
            class Generic(val genericFailure: CoreFailure) : Failure()
        }
    }
}
