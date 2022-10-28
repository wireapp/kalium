package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.TeamsApiV2

internal open class TeamsApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : TeamsApiV2(authenticatedNetworkClient)
