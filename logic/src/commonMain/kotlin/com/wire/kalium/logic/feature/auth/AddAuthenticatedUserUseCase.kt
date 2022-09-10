package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
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
        userId: UserId,
        serverConfigId: String,
        ssoId: SsoId?,
        authTokens: AuthTokens,
        replace: Boolean = false
    ): Result =
        sessionRepository.doesSessionExist(userId).fold(
            {
                Result.Failure.Generic(it)
            }, {
                when (it) {
                    true -> {
                        val forceReplace = sessionRepository.doesSessionExist(userId).fold(
                            { replace },
                            { doesValidSessionExist -> (doesValidSessionExist || replace) }
                        )
                        onUserExist(userId,serverConfigId, ssoId, authTokens, forceReplace)
                    }

                    false -> storeUser(userId,serverConfigId, ssoId, authTokens)
                }
            }
        )

    private suspend fun storeUser(
        userId: UserId,
        serverConfigId: String,
        ssoId: SsoId?,
        authTokens: AuthTokens
    ): Result =
        sessionRepository.storeSession(userId, serverConfigId, ssoId, authTokens)
            .onSuccess {
                sessionRepository.updateCurrentSession(userId)
            }.fold(
                { Result.Failure.Generic(it) },
                { Result.Success(userId) }
            )

    private suspend fun onUserExist(
        userId: UserId,
        newServerConfigId: String,
        ssoId: SsoId?,
        newAuthTokens: AuthTokens,
        replace: Boolean
    ): Result =
        when (replace) {
            true -> {
                sessionRepository.fullAccountInfo(userId).fold(
                    // in case of the new session have a different server configurations the new session should not be added
                    { Result.Failure.Generic(it) },
                    { oldSession ->
                        val newServerConfig = serverConfigRepository.configById(newServerConfigId).fold({return Result.Failure.Generic(it)}, {it})
                        if (oldSession.serverConfig.links == newServerConfig.links) {
                            storeUser(userId, newServerConfigId, ssoId, newAuthTokens)
                        } else Result.Failure.UserAlreadyExists
                    }
                )
            }

            false -> Result.Failure.UserAlreadyExists
        }
}
