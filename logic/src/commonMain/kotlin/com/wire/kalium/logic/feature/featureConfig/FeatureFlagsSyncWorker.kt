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
package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.logStructuredJson
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Worker that periodically syncs feature flags.
 */
interface FeatureFlagsSyncWorker {
    suspend fun execute()
}

internal class FeatureFlagSyncWorkerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val syncFeatureConfigs: SyncFeatureConfigsUseCase,
    kaliumLogger: KaliumLogger,
    private val minIntervalBetweenPulls: Duration = MIN_INTERVAL_BETWEEN_PULLS,
    private val clock: Clock = Clock.System
) : FeatureFlagsSyncWorker {

    private var lastPullInstant: Instant? = null
    private val logger = kaliumLogger.withTextTag("FeatureFlagSyncWorker")

    override suspend fun execute() {
        logger.d("Starting to monitor")
        incrementalSyncRepository.incrementalSyncState.filter {
            it is IncrementalSyncStatus.Live
        }.collect {
            syncFeatureFlagsIfNeeded()
        }
    }

    private suspend fun FeatureFlagSyncWorkerImpl.syncFeatureFlagsIfNeeded() {
        val now = clock.now()
        val wasLastPullRecent = lastPullInstant?.let { lastPull ->
            lastPull + minIntervalBetweenPulls > now
        } ?: false
        logger.logStructuredJson(
            level = KaliumLogLevel.INFO,
            leadingMessage = "syncFeatureFlagsIfNeeded",
            jsonStringKeyValues = mapOf(
                "lastPullInstant" to lastPullInstant,
                "wasLastPullRecent" to wasLastPullRecent
            )
        )
        if (!wasLastPullRecent) {
            logger.i("Synching feature configs and updating lastPullInstant")
            syncFeatureConfigs()
            lastPullInstant = now
        }
    }

    private companion object {
        val MIN_INTERVAL_BETWEEN_PULLS = 60.minutes
    }
}
