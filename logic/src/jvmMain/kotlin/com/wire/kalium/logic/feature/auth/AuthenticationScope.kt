package com.wire.kalium.logic.feature.auth

import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.persistence.client.SessionLocalDataSource

actual class AuthenticationScope(
    loginNetworkContainer: LoginNetworkContainer,
    clientLabel: String
) : AuthenticationScopeCommon(loginNetworkContainer, clientLabel) {
    override val sessionLocalDataSource: SessionLocalDataSource = SessionLocalDataSource()
}
