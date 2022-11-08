package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.AssetApiV2

internal open class AssetApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : AssetApiV2(authenticatedNetworkClient)
