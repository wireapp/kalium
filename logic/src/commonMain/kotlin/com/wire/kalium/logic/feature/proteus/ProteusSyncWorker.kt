/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.proteus

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Represents an interface for a ProteusSyncWorker,
 * which is should keep the [ProteusClient] healthy.
 *
 * It will wait until the incremental sync is live and then refill the pre-keys if needed.
 */
internal interface ProteusSyncWorker {
    suspend fun execute()
}

/**
 * Base implementation of [ProteusSyncWorker].
 *
 * @param incrementalSyncRepository The incremental sync repository.
 * @param proteusPreKeyRefiller The proteus pre-key refiller.
 * @param preKeyRepository The pre-key repository.
 * @param minInterValBetweenRefills The minimum interval between prekey refills.
 */
internal class ProteusSyncWorkerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val proteusPreKeyRefiller: ProteusPreKeyRefiller,
    private val preKeyRepository: PreKeyRepository,
    private val minInterValBetweenRefills: Duration = MIN_INTEVAL_BETWEEN_REFILLS
) : ProteusSyncWorker {

    override suspend fun execute() {
        preKeyRepository.lastPreKeyRefillCheckInstantFlow()
            .collectLatest { lastRefill ->
                val now = Clock.System.now()
                val nextCheckTime = lastRefill?.plus(minInterValBetweenRefills) ?: now
                val delayUntilNextCheck = nextCheckTime - now
                delay(delayUntilNextCheck)
                waitUntilLiveAndRefillPreKeysIfNeeded()
                preKeyRepository.setLastPreKeyRefillCheckInstant(Clock.System.now())
            }
    }

    private suspend fun waitUntilLiveAndRefillPreKeysIfNeeded() {
        incrementalSyncRepository.incrementalSyncState
            .filter { it is IncrementalSyncStatus.Live }
            .collect {
                proteusPreKeyRefiller.refillIfNeeded()
            }
    }

    private companion object {
        val MIN_INTEVAL_BETWEEN_REFILLS = 1.days
    }
}
