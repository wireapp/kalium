package com.wire.kalium.logic

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.WorkScheduler
import com.wire.kalium.network.AuthenticatedNetworkContainer

class AuthenticatedDataSourceSet(
    val authenticatedNetworkContainer: AuthenticatedNetworkContainer,
    val proteusClient: ProteusClient,
    val workScheduler: WorkScheduler,
    val syncManager: SyncManager
)
