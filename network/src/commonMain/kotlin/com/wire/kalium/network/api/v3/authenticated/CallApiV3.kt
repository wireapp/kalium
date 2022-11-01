package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.CallApiV2

internal open class CallApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : CallApiV2(authenticatedNetworkClient)
