package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.UserDetailsApiV2

internal open class UserDetailsApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : UserDetailsApiV2(authenticatedNetworkClient)
