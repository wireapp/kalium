package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onSuccess

class AddAuthenticatedUserUseCase internal constructor(
    private val sessionRepository: SessionRepository,
    private val serverConfigRepository: ServerConfigRepository
) {
    sealed class Result {
        data class Success(val userId: UserId) : Result()
        sealed class Failure : Result() {
            object UserAlreadyExists : Failure()
            class Generic(val genericFailure: CoreFailure) : Failure()
        }
    }

    suspend operator fun invoke(
        serverConfigId: String,
        ssoId: SsoId?,
        authTokens: AuthTokens,
        proxyCredentials: ProxyCredentials?,
        replace: Boolean = false
    ): Result =
        sessionRepository.doesValidSessionExist(authTokens.userId).fold(
            {
                Result.Failure.Generic(it)
            }, { doesValidSessionExist ->
                when (doesValidSessionExist) {
                    true -> onUserExist(serverConfigId, ssoId, authTokens, proxyCredentials, replace)
                    false -> storeUser(serverConfigId, ssoId, authTokens, proxyCredentials)
                }
            }
        )

    private suspend fun storeUser(
        serverConfigId: String,
        ssoId: SsoId?,
        authTokens: AuthTokens,
        proxyCredentials: ProxyCredentials?
    ): Result =
        sessionRepository.storeSession(serverConfigId, ssoId, authTokens, proxyCredentials)
            .onSuccess {
                sessionRepository.updateCurrentSession(authTokens.userId)
            }.fold(
                { Result.Failure.Generic(it) },
                { Result.Success(authTokens.userId) }
            )

    private suspend fun onUserExist(
        newServerConfigId: String,
        ssoId: SsoId?,
        newAuthTokens: AuthTokens,
        proxyCredentials: ProxyCredentials?,
        replace: Boolean
    ): Result =
        when (replace) {
            true -> {
                sessionRepository.fullAccountInfo(newAuthTokens.userId).fold(
                    // in case of the new session have a different server configurations the new session should not be added
                    { Result.Failure.Generic(it) },
                    { oldSession ->
                        val newServerConfig =
                            serverConfigRepository.configById(newServerConfigId).fold({ return Result.Failure.Generic(it) }, { it })
                        if (oldSession.serverConfig.links == newServerConfig.links) {
                            storeUser(
                                serverConfigId = newServerConfigId,
                                ssoId = ssoId,
                                authTokens = newAuthTokens,
                                proxyCredentials = proxyCredentials
                            )
                        } else Result.Failure.UserAlreadyExists
                    }
                )
            }

            false -> Result.Failure.UserAlreadyExists
        }
}
