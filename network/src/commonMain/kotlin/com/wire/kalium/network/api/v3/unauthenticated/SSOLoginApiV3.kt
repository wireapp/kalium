package com.wire.kalium.network.api.v3.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.v2.unauthenticated.SSOLoginApiV2

internal open class SSOLoginApiV3 internal constructor(
    unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : SSOLoginApiV2(unauthenticatedNetworkClient)
