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
package com.wire.kalium.logic.feature.proteus

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
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
 * @param minIntervalBetweenRefills The minimum interval between prekey refills.
 */
internal class ProteusSyncWorkerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val proteusPreKeyRefiller: ProteusPreKeyRefiller,
    private val preKeyRepository: PreKeyRepository,
    private val minIntervalBetweenRefills: Duration = MIN_INTERVAL_BETWEEN_REFILLS,
    kaliumLogger: KaliumLogger,
) : ProteusSyncWorker {

    private val logger = kaliumLogger.withTextTag("ProteusSyncWorker")

    override suspend fun execute() {
        logger.d("Starting to monitor")
        preKeyRepository.lastPreKeyRefillCheckInstantFlow()
            .collectLatest { lastRefill ->
                val now = Clock.System.now()
                val nextCheckTime = lastRefill?.plus(minIntervalBetweenRefills) ?: now
                val delayUntilNextCheck = (nextCheckTime - now).coerceIn(ZERO, minIntervalBetweenRefills)
                logger.logStructuredJson(
                    level = KaliumLogLevel.DEBUG,
                    leadingMessage = "Delaying until next check",
                    jsonStringKeyValues = mapOf(
                        "lastRefillPerformedAt" to lastRefill?.toIsoDateTimeString(),
                        "nextCheckTimeAt" to nextCheckTime.toIsoDateTimeString(),
                        "delayingFor" to delayUntilNextCheck.toString(),
                    )
                )
                delay(delayUntilNextCheck)
                waitUntilLiveAndRefillPreKeysIfNeeded()
                preKeyRepository.setLastPreKeyRefillCheckInstant(Clock.System.now())
            }
    }

    private suspend fun waitUntilLiveAndRefillPreKeysIfNeeded() {
        logger.i("Waiting until live to refill prekeys if needed")
        incrementalSyncRepository.incrementalSyncState
            .filter { it is IncrementalSyncStatus.Live }
            .firstOrNull()
            ?.let {
                logger.i("Refilling prekeys if needed")
                proteusPreKeyRefiller.refillIfNeeded()
            }
    }

    private companion object {
        val MIN_INTERVAL_BETWEEN_REFILLS = 1.days
    }
}
