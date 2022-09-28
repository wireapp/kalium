package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.FeatureConfigApiV0

internal class FeatureConfigApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : FeatureConfigApiV0(authenticatedNetworkClient)
