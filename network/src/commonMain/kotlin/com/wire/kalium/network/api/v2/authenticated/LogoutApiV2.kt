package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.LogoutApiV0
import com.wire.kalium.network.session.SessionManager

internal open class LogoutApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    sessionManager: SessionManager
) : LogoutApiV0(authenticatedNetworkClient, sessionManager)
