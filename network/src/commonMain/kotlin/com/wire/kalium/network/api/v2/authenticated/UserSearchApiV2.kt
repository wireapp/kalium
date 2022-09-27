package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.UserSearchApiV0

internal open class UserSearchApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : UserSearchApiV0(authenticatedNetworkClient)
