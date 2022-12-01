package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.PropertiesApiV2

internal open class PropertiesApiV3 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
) : PropertiesApiV2(authenticatedNetworkClient)
