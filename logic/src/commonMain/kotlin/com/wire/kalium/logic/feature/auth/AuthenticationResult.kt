package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure

sealed class AuthenticationResult {
    class Success(val userSession: AuthSession) : AuthenticationResult()

    sealed class Failure : AuthenticationResult() {
        object InvalidCredentials : Failure()
        object InvalidUserIdentifier : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
