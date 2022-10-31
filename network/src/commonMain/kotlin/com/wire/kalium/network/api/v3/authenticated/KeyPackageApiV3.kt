package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.KeyPackageApiV2

internal open class KeyPackageApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : KeyPackageApiV2(authenticatedNetworkClient)
