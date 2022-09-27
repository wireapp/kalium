package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.UserDetailsApiV0

internal open class UserDetailsApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : UserDetailsApiV0(authenticatedNetworkClient)
