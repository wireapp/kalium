package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.ConnectionApiV0

internal open class ConnectionApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ConnectionApiV0(authenticatedNetworkClient)
