package com.wire.kalium.logic

import com.wire.kalium.network.AuthenticatedNetworkContainer

interface AuthenticatedDataSourcesProvider {
    val authenticatedNetworkContainer: AuthenticatedNetworkContainer
}
