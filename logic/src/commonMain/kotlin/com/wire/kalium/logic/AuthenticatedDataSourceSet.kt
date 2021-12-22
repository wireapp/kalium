package com.wire.kalium.logic

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.network.AuthenticatedNetworkContainer

class AuthenticatedDataSourceSet(
    val authenticatedNetworkContainer: AuthenticatedNetworkContainer,
    val proteusClient: ProteusClient
)
