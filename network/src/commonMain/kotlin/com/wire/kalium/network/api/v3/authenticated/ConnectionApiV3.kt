package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.ConnectionApiV2

internal open class ConnectionApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ConnectionApiV2(authenticatedNetworkClient)
