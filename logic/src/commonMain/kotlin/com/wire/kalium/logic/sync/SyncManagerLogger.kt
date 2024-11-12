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
package com.wire.kalium.logic.sync

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.logStructuredJson
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Logs the sync process by providing structured logs.
 * It logs the sync process start and completion with the syncId as a unique identifier.
 */
internal class SyncManagerLogger(
    private val logger: KaliumLogger,
    private val syncId: String,
    private val syncType: SyncType,
    private val syncStartedMoment: Instant
) {

    /**
     * Logs the sync process start.
     */
    fun logSyncStarted() {
        logger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SYNC).logStructuredJson(
            level = KaliumLogLevel.INFO,
            leadingMessage = "Started sync process",
            jsonStringKeyValues = mapOf(
                "syncMetadata" to mapOf(
                    "id" to syncId,
                    "status" to SyncStatus.STARTED.name,
                    "type" to syncType.name
                )
            )
        )
    }

    /**
     * Logs the sync process completion.
     * Optionally, it can pass the duration of the sync process,
     * useful for incremental sync that can happen between collecting states.
     *
     * @param duration optional the duration of the sync process.
     */
    fun logSyncCompleted(duration: Duration = Clock.System.now() - syncStartedMoment) {
        val logMap = mapOf(
            "id" to syncId,
            "status" to SyncStatus.COMPLETED.name,
            "type" to syncType.name,
            "performanceData" to mapOf("timeTakenInMillis" to duration.inWholeMilliseconds)
        )

        logger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SYNC).logStructuredJson(
            level = KaliumLogLevel.INFO,
            leadingMessage = "Completed sync process",
            jsonStringKeyValues = mapOf("syncMetadata" to logMap)
        )
    }
}

internal enum class SyncStatus {
    STARTED,
    COMPLETED
}

internal enum class SyncType {
    SLOW,
    INCREMENTAL
}

/**
 * Provides a new [SyncManagerLogger] instance with the given parameters.
 * @param syncType the [SyncType] that will log.
 * @param syncId the unique identifier for the sync process.
 * @param syncStartedMoment the moment when the sync process started.
 */
internal fun KaliumLogger.provideNewSyncManagerLogger(
    syncType: SyncType,
    syncId: String = uuid4().toString(),
    syncStartedMoment: Instant = Clock.System.now()
) = SyncManagerLogger(this, syncId, syncType, syncStartedMoment)
