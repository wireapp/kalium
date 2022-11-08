package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.PreKeyApiV2

internal open class PreKeyApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : PreKeyApiV2(authenticatedNetworkClient)
