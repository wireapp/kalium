package com.wire.kalium.network.api.v2.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.v0.unauthenticated.LoginApiV0

internal open class LoginApiV2 internal constructor(
    unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : LoginApiV0(unauthenticatedNetworkClient)
