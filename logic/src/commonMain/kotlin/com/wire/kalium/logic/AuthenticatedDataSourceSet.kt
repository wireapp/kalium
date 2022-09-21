package com.wire.kalium.logic

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.UserPrefProvider

@Suppress("LongParameterList") // Suppressed as it's an old issue
class AuthenticatedDataSourceSet(
    val authenticatedRootDir: String,
    val authenticatedNetworkContainer: AuthenticatedNetworkContainer,
    val proteusClient: ProteusClient,
    val userSessionWorkScheduler: UserSessionWorkScheduler,
    val userDatabaseProvider: UserDatabaseProvider,
    val userPrefProvider: UserPrefProvider
)
