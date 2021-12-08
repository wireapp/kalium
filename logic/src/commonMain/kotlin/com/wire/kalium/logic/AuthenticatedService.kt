package com.wire.kalium.logic

import com.wire.kalium.network.AuthenticatedNetworkContainer

abstract class AuthenticatedService(
    protected val userSession: UserSession,
    protected val authenticatedNetworkContainer : AuthenticatedNetworkContainer
) {
}
