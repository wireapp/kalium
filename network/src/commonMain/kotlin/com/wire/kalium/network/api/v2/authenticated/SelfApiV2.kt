package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.SelfApiV0

internal open class SelfApiV2 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : SelfApiV0(authenticatedNetworkClient)
