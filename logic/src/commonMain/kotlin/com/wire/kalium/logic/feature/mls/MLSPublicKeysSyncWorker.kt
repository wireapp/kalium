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
package com.wire.kalium.logic.feature.mls

import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.days

/**
 * Worker that periodically syncs MLS public keys.
 * It will wait until the interval passes and incremental sync is live and then fetch MLS public keys.
 */
interface MLSPublicKeysSyncWorker {
    suspend fun schedule()
    suspend fun executeImmediately()
}

internal class MLSPublicKeysSyncWorkerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val mlsPublicKeysRepository: MLSPublicKeysRepository,
    private val minIntervalBetweenRefills: Duration = MIN_INTERVAL_BETWEEN_REFILLS,
    private val lastFetchInstantFlow: MutableStateFlow<Instant> = MutableStateFlow(Instant.DISTANT_PAST),
    kaliumLogger: KaliumLogger,
) : MLSPublicKeysSyncWorker {

    private val logger = kaliumLogger.withTextTag("MLSPublicKeysSyncWorker")
    override suspend fun schedule() {
        logger.d("Starting to monitor")
        lastFetchInstantFlow
            .onCompletion {
                logger.i("Stopping to monitor")
            }
            .collectLatest { lastFetch ->
                val now = Clock.System.now()
                val nextCheckTime = lastFetch.plus(minIntervalBetweenRefills)
                val delayUntilNextCheck = (nextCheckTime - now).coerceIn(ZERO, minIntervalBetweenRefills)
                logger.logStructuredJson(
                    level = KaliumLogLevel.DEBUG,
                    leadingMessage = "Delaying until next fetch",
                    jsonStringKeyValues = mapOf(
                        "lastFetchPerformedAt" to lastFetch.toIsoDateTimeString(),
                        "nextFetchTimeAt" to nextCheckTime.toIsoDateTimeString(),
                        "delayingFor" to delayUntilNextCheck.toString(),
                    )
                )
                delay(delayUntilNextCheck)
                waitUntilLiveAndFetchMLSPublicKeys()
            }
    }

    override suspend fun executeImmediately() {
        logger.d("Executing immediately")
        waitUntilLiveAndFetchMLSPublicKeys()
    }

    private suspend fun waitUntilLiveAndFetchMLSPublicKeys() {
        logger.i("Waiting until live to fetch MLS public keys")
        incrementalSyncRepository.incrementalSyncState
            .filter { it is IncrementalSyncStatus.Live }
            .firstOrNull()
            ?.let {
                logger.i("Fetching MLS public keys")
                mlsPublicKeysRepository.fetchKeys()
                lastFetchInstantFlow.value = Clock.System.now()
            }
    }

    private companion object {
        val MIN_INTERVAL_BETWEEN_REFILLS = 1.days
    }
}
