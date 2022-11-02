package com.wire.kalium.network.api.v3.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.v2.unauthenticated.LoginApiV2

internal open class LoginApiV3 internal constructor(
    unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : LoginApiV2(unauthenticatedNetworkClient)
