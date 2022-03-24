package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SelfUser


class AddAuthenticatedUserUseCase(
    private val sessionRepository: SessionRepository
) {
    sealed class Result {
        object Success : Result()
        sealed class Failure : Result() {
            object UserAlreadyExists : Failure()
            class Generic(val genericFailure: CoreFailure) : Failure()
        }
    }

    operator fun invoke(user: SelfUser, authSession: AuthSession, replace: Boolean = false): Result =
        sessionRepository.doesSessionExist(authSession.userId).fold(
            {
                Result.Failure.Generic(it)
            }, {
                when (it) {
                    true -> onUserExist(authSession, replace)
                    false -> {
                        storeUser(authSession)
                    }
                }
            }
        )

    private fun storeUser(authSession: AuthSession): Result {
        sessionRepository.storeSession(authSession)
        sessionRepository.updateCurrentSession(authSession.userId)
        return Result.Success
    }

    private fun onUserExist(newSession: AuthSession, replace: Boolean): Result =
        when (replace) {
            true -> {
                sessionRepository.userSession(newSession.userId).fold(
                    // in case of the new session have a different server configurations the new session should not be added
                    { Result.Failure.Generic(it) }, { oldSession ->
                        if (oldSession.serverConfig == newSession.serverConfig) {
                            storeUser(newSession)
                        } else Result.Failure.UserAlreadyExists
                    }
                )
            }
            false -> Result.Failure.UserAlreadyExists
        }
}
