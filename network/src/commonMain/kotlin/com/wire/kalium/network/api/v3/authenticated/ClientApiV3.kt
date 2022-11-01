package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.ClientApiV2

internal open class ClientApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ClientApiV2(authenticatedNetworkClient)
