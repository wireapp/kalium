package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.AssetApiV0

internal open class AssetApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : AssetApiV0(authenticatedNetworkClient)
