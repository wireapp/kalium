package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.PreKeyApiV0

internal open class PreKeyApiV2 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : PreKeyApiV0(authenticatedNetworkClient)
