package com.wire.kalium.logic.feature

import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer

class AuthenticatedDataSourceSet(
    val authenticatedRootDir: String,
    val authenticatedNetworkContainer: AuthenticatedNetworkContainer,
    val proteusClientProvider: ProteusClientProvider,
    val userSessionWorkScheduler: UserSessionWorkScheduler
)
