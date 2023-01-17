package com.wire.kalium.logic.feature.auth.autoVersioningAuth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.CoreLogicCommon
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for obtaining the authentication scope for the current version of the app.
 * It will try to validate if the current client is able to talk to a specific backend version.
 */
class AutoVersionAuthScopeUseCase(
    private val kaliumConfigs: KaliumConfigs,
    private val serverLinks: ServerConfig.Links,
    private val coreLogic: CoreLogicCommon,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {
    suspend operator fun invoke(proxyAuthentication: ProxyAuthentication = ProxyAuthentication.None): Result =
        withContext(dispatcher.default) {
            coreLogic.getGlobalScope().serverConfigRepository.getOrFetchMetadata(serverLinks).fold({
                handleError(it)
            }, { serverConfig ->
                // Backend team doesn't want any clients using the development APIs in production, so
                // until they disable access to the APIs we'll have this safeguard in the client to
                // prevent any accidental usage.
                if (kaliumConfigs.developmentApiEnabled && serverConfig.links == ServerConfig.PRODUCTION) {
                    return@fold Result.Failure.Generic(CoreFailure.DevelopmentAPINotAllowedOnProduction)
                }
                val proxyCredentials = when (proxyAuthentication) {
                    is ProxyAuthentication.None -> null
                    is ProxyAuthentication.UsernameAndPassword -> proxyAuthentication.proxyCredentials
                }
                Result.Success(coreLogic.getAuthenticationScope(serverConfig, proxyCredentials))
            })
        }

    private fun handleError(coreFailure: CoreFailure): Result.Failure =
        when (coreFailure) {
            is ServerConfigFailure.NewServerVersion -> Result.Failure.TooNewVersion
            is ServerConfigFailure.UnknownServerVersion -> Result.Failure.UnknownServerVersion
            else -> Result.Failure.Generic(coreFailure)
        }.also { kaliumLogger.e(coreFailure.toString()) }

    sealed class Result {
        class Success(val authenticationScope: AuthenticationScope) : Result()

        sealed class Failure : Result() {
            object UnknownServerVersion : Failure()
            object TooNewVersion : Failure()
            class Generic(val genericFailure: CoreFailure) : Failure()
        }
    }

    sealed interface ProxyAuthentication {
        object None : ProxyAuthentication

        class UsernameAndPassword(val proxyCredentials: ProxyCredentials) : ProxyAuthentication
    }
}
