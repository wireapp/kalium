package com.wire.kalium.logic.feature

import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.kmmSettings.UserPrefBuilder

@Suppress("LongParameterList") // Suppressed as it's an old issue
class AuthenticatedDataSourceSet(
    val authenticatedRootDir: String,
    val authenticatedNetworkContainer: AuthenticatedNetworkContainer,
    val proteusClientProvider: ProteusClientProvider,
    val userSessionWorkScheduler: UserSessionWorkScheduler,
    val userDatabaseBuilder: UserDatabaseBuilder,
    val userPrefBuilder: UserPrefBuilder
)
