package com.wire.kalium.logic.failure

import com.wire.kalium.logic.CoreFailure

sealed class AuthenticationFailure : CoreFailure.FeatureFailure() {
    object InvalidCredentials: AuthenticationFailure()
}

