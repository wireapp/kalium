package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.UserSearchApiV2

internal open class UserSearchApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : UserSearchApiV2(authenticatedNetworkClient)
