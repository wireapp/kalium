package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.ClientApiV0

internal open class ClientApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ClientApiV0(authenticatedNetworkClient)
