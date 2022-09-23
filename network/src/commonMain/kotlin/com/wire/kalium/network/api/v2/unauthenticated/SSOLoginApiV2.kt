package com.wire.kalium.network.api.v2.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.v0.unauthenticated.SSOLoginApiV0

internal open class SSOLoginApiV2 internal constructor(
    unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : SSOLoginApiV0(unauthenticatedNetworkClient)
