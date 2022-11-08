package com.wire.kalium.network.api.v3.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.v2.unauthenticated.RegisterApiV2

internal open class RegisterApiV3 internal constructor(
    unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : RegisterApiV2(unauthenticatedNetworkClient)
