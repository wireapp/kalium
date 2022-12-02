package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.PropertiesApiV0

internal open class PropertiesApiV2 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
) : PropertiesApiV0(authenticatedNetworkClient)
