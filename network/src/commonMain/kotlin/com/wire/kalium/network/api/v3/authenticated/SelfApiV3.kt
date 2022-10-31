package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.SelfApiV2

internal open class SelfApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : SelfApiV2(authenticatedNetworkClient)
