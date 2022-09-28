package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.CallApiV0

internal open class CallApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : CallApiV0(authenticatedNetworkClient)
