package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.LogoutApiV2
import com.wire.kalium.network.session.SessionManager

internal open class LogoutApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    sessionManager: SessionManager
) : LogoutApiV2(authenticatedNetworkClient, sessionManager)
