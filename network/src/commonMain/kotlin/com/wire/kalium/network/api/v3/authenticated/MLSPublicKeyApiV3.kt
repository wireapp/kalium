package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.MLSPublicKeyApiV2

internal open class MLSPublicKeyApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : MLSPublicKeyApiV2(authenticatedNetworkClient)
