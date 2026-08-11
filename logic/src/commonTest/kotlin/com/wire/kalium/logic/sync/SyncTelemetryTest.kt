/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.util.ExponentialDurationHelper
import com.wire.kalium.network.CurrentNetwork
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SyncTelemetryTest {

    @Test
    fun givenNetworkFailure_whenBuildingTelemetry_thenShouldIncludeTypesWithoutExceptionMessage() {
        val data = NetworkFailure.NoNetworkConnection(IOException("sensitive details")).telemetryData()

        assertEquals("NoNetworkConnection", data["failureType"])
        assertEquals("IOException", data["causeType"])
        assertFalse(data.values.contains("sensitive details"))
    }

    @Test
    fun givenSyncTelemetry_whenLogging_thenShouldWriteStableStructuredFields() {
        val writer = RecordingLogWriter()
        val logger = KaliumLogger(
            config = KaliumLogger.Config(
                initialLevel = KaliumLogLevel.DEBUG,
                initialLogWriterList = listOf(writer),
            ),
            tag = "SyncTelemetryTest",
        )

        logger.logSyncTelemetry(
            event = SyncTelemetryEvent.RETRY_TRIGGERED,
            component = SyncTelemetryComponent.INCREMENTAL,
            data = mapOf(
                "trigger" to "NETWORK_RECONNECTED",
                "retryDelayInMillis" to 1_000L,
                "backoffReset" to true,
            )
        )

        val entry = writer.entries.single()
        assertEquals(Severity.Info, entry.severity)
        assertEquals("SyncTelemetryTest", entry.tag)
        assertContains(entry.message, "Sync telemetry:")
        assertContains(entry.message, "\"schemaVersion\":1")
        assertContains(entry.message, "\"event\":\"RETRY_TRIGGERED\"")
        assertContains(entry.message, "\"component\":\"INCREMENTAL\"")
        assertContains(entry.message, "\"trigger\":\"NETWORK_RECONNECTED\"")
        assertContains(entry.message, "\"retryDelayInMillis\":1000")
        assertContains(entry.message, "\"backoffReset\":true")
    }

    @Test
    fun givenSyncProcessEvents_whenLogging_thenShouldUseTheSharedFlatTelemetryEnvelope() {
        val writer = RecordingLogWriter()
        val logger = recordingLogger(writer)
        val syncLogger = SyncManagerLogger(
            logger = logger,
            syncId = "sync-id",
            syncType = SyncType.SLOW,
            syncStartedMoment = Instant.fromEpochMilliseconds(0),
        )

        syncLogger.logSyncStarted()
        syncLogger.logSyncCompleted(2.seconds)

        val started = writer.entries[0].message
        val completed = writer.entries[1].message
        assertContains(started, "Sync telemetry:")
        assertContains(started, "\"event\":\"SYNC_PROCESS_STARTED\"")
        assertContains(started, "\"syncId\":\"sync-id\"")
        assertContains(completed, "Sync telemetry:")
        assertContains(completed, "\"event\":\"SYNC_PROCESS_COMPLETED\"")
        assertContains(completed, "\"durationInMillis\":2000")
        assertFalse(started.contains("syncMetadata"))
        assertFalse(completed.contains("syncMetadata"))
    }

    @Test
    fun givenAConnectedNetwork_whenWaitingToRetry_thenBothRetryEventsShouldContainItsNetworkId() = runTest {
        val writer = RecordingLogWriter()
        val logger = recordingLogger(writer)
        val observer = object : NetworkStateObserver {
            private val networkState = MutableStateFlow<NetworkState>(NetworkState.ConnectedWithInternet)
            private val currentNetwork = MutableStateFlow<CurrentNetwork?>(
                CurrentNetwork(id = "network-id", type = CurrentNetwork.Type.WIFI, hasInternetAccess = true)
            )

            override fun observeNetworkState(): StateFlow<NetworkState> = networkState
            override fun observeCurrentNetwork(): StateFlow<CurrentNetwork?> = currentNetwork
            override suspend fun delayUntilConnectedWithInternetAgain(delay: Duration): Boolean = false
        }

        observer.delayBeforeSyncRetry(
            retryDelay = 2.seconds,
            exponentialDurationHelper = ExponentialDurationHelper(1.seconds, 10.seconds),
            logger = logger,
            syncType = SyncType.INCREMENTAL,
        )

        assertEquals(2, writer.entries.size)
        writer.entries.forEach { assertContains(it.message, "\"networkId\":\"network-id\"") }
        assertContains(writer.entries[0].message, "\"event\":\"RETRY_WAIT_STARTED\"")
        assertContains(writer.entries[1].message, "\"event\":\"RETRY_TRIGGERED\"")
    }

    private fun recordingLogger(writer: RecordingLogWriter) = KaliumLogger(
        config = KaliumLogger.Config(
            initialLevel = KaliumLogLevel.DEBUG,
            initialLogWriterList = listOf(writer),
        ),
        tag = "SyncTelemetryTest",
    )

    private class RecordingLogWriter : LogWriter() {
        val entries = mutableListOf<Entry>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            entries += Entry(severity, message, tag)
        }
    }

    private data class Entry(
        val severity: Severity,
        val message: String,
        val tag: String,
    )
}
