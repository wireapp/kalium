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
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MLSClientManager is responsible for registering an MLS client when a user
 * upgrades to an MLS supported build.
 */
@Mockable
internal interface MLSClientManager {
    suspend operator fun invoke()
}

@Suppress("LongParameterList")
internal class MLSClientManagerImpl internal constructor(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase,
    private val syncStateObserver: SyncStateObserver,
    private val slowSyncRepository: Lazy<SlowSyncRepository>,
    private val clientRepository: Lazy<ClientRepository>,
    private val registerMLSClient: Lazy<RegisterMLSClientUseCase>,
    private val userCoroutineScope: CoroutineScope,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : MLSClientManager {

    private val registrationMutex = Mutex()

    override suspend operator fun invoke() {
        syncStateObserver.waitUntilLiveOrFailure().onSuccess {
            // Keep the lock in the user scope: cancelling the caller must not unlock ongoing registration.
            userCoroutineScope.async(dispatchers.io) {
                registrationMutex.withLock {
                    registerMLSClientIfPossibleAndNeeded()
                }
            }.await()
        }
    }

    private suspend fun registerMLSClientIfPossibleAndNeeded() {
        clientRepository.value.hasRegisteredMLSClient().flatMap { isMLSClientRegistered ->
            if (!isMLSClientRegistered && isAllowedToRegisterMLSClient()) {
                currentClientIdProvider().flatMap { clientId ->
                    kaliumLogger.i("No existing MLS Client, registering..")
                    registerMLSClient.value(clientId).onSuccess { mlsClientRegistrationResult ->
                        kaliumLogger.i("Registering mls client result: $mlsClientRegistrationResult")
                        if (mlsClientRegistrationResult is RegisterMLSClientResult.Success) {
                            kaliumLogger.i("Triggering slow sync after enabling MLS")
                            slowSyncRepository.value.clearLastSlowSyncCompletionInstant()
                        }
                    }
                }
            } else {
                Either.Right(Unit)
            }
        }
    }
}
