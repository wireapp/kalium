package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

class AddAuthenticatedUserUseCase(
    private val sessionRepository: SessionRepository
) {
    sealed class Result {
        data class Success(val userId: UserId) : Result()
        sealed class Failure : Result() {
            object UserAlreadyExists : Failure()
            class Generic(val genericFailure: CoreFailure) : Failure()
        }
    }

    operator fun invoke(authSession: AuthSession, ssoId: SsoId?, replace: Boolean = false): Result =
        sessionRepository.doesSessionExist(authSession.session.userId).fold(
            {
                Result.Failure.Generic(it)
            }, {
                when (it) {
                    true -> {
                        val forceReplace = sessionRepository.userSession(authSession.session.userId).fold(
                            { replace },
                            { existSession -> existSession.session is AuthSession.Session.Invalid || replace }
                        )
                        onUserExist(authSession, ssoId, forceReplace)
                    }

                    false -> storeUser(authSession, ssoId)
                }
            }
        )

    private fun storeUser(authSession: AuthSession, ssoId: SsoId?): Result {
        sessionRepository.storeSession(authSession, ssoId)
        sessionRepository.updateCurrentSession(authSession.session.userId)
        return Result.Success(authSession.session.userId)
    }

    private fun onUserExist(newSession: AuthSession, ssoId: SsoId?, replace: Boolean): Result =
        when (replace) {
            true -> {
                sessionRepository.userSession(newSession.session.userId).fold(
                    // in case of the new session have a different server configurations the new session should not be added
                    { Result.Failure.Generic(it) }, { oldSession ->
                        if (oldSession.serverLinks == newSession.serverLinks) {
                            storeUser(newSession.copy(serverLinks = oldSession.serverLinks), ssoId)
                        } else Result.Failure.UserAlreadyExists
                    }
                )
            }

            false -> Result.Failure.UserAlreadyExists
        }
}
