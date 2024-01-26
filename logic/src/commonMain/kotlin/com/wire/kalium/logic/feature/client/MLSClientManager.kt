/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

interface MLSClientManager

/**
 * MLSClientManager is responsible for registering an MLS client when a user
 * upgrades to an MLS supported build.
 */
@Suppress("LongParameterList")
internal class MLSClientManagerImpl(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val slowSyncRepository: Lazy<SlowSyncRepository>,
    private val clientRepository: Lazy<ClientRepository>,
    private val registerMLSClient: Lazy<RegisterMLSClientUseCase>,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : MLSClientManager {
    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)

    private val scope = CoroutineScope(dispatcher)

    private var job: Job? = null

    init {
        job = scope.launch {
            incrementalSyncRepository.incrementalSyncState.collect { syncState ->
                ensureActive()
                if (syncState is IncrementalSyncStatus.Live &&
                    isAllowedToRegisterMLSClient()
                ) {
                    registerMLSClientIfNeeded()
                }
            }
        }
    }

    private suspend fun registerMLSClientIfNeeded() {
        clientRepository.value.hasRegisteredMLSClient().flatMap {
            if (!it) {
                currentClientIdProvider().flatMap { clientId ->
                    kaliumLogger.i("No existing MLS Client, registering..")
                    registerMLSClient.value(clientId).onSuccess { mlsClientRegistrationResult ->
                        kaliumLogger.i("Registering mls client result: $mlsClientRegistrationResult")
                        kaliumLogger.i("Triggering slow sync after enabling MLS")
                        slowSyncRepository.value.clearLastSlowSyncCompletionInstant()
                    }
                }
            } else {
                Either.Right(Unit)
            }
        }
    }
}
