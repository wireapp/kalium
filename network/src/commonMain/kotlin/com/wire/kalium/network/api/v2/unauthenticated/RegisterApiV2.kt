package com.wire.kalium.network.api.v2.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.v0.unauthenticated.RegisterApiV0

internal open class RegisterApiV2 internal constructor(
    unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : RegisterApiV0(unauthenticatedNetworkClient)
