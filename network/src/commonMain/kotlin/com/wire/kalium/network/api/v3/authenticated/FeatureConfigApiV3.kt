package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.FeatureConfigApiV2

internal open class FeatureConfigApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : FeatureConfigApiV2(authenticatedNetworkClient)
