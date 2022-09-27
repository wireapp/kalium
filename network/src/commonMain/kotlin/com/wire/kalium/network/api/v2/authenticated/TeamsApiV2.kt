package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.TeamsApiV0

internal open class TeamsApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : TeamsApiV0(authenticatedNetworkClient)
