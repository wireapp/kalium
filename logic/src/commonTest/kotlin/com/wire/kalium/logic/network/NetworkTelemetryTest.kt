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
package com.wire.kalium.logic.network

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class NetworkTelemetryTest {

    @Test
    fun givenNetworkTelemetry_whenLogging_thenShouldWriteStableStructuredFields() {
        val writer = RecordingLogWriter()
        val logger = KaliumLogger(
            config = KaliumLogger.Config(
                initialLevel = KaliumLogLevel.DEBUG,
                initialLogWriterList = listOf(writer),
            ),
            tag = "NetworkTelemetryTest",
        )

        logger.logNetworkTelemetry(
            event = NetworkTelemetryEvent.NETWORK_AVAILABLE,
            data = mapOf(
                "networkId" to "network-id",
                "networkState" to "CONNECTED_WITHOUT_INTERNET",
            )
        )

        val entry = writer.entries.single()
        assertEquals(Severity.Info, entry.severity)
        assertContains(entry.message, "Network telemetry:")
        assertContains(entry.message, "\"schemaVersion\":1")
        assertContains(entry.message, "\"event\":\"NETWORK_AVAILABLE\"")
        assertContains(entry.message, "\"component\":\"NETWORK_OBSERVER\"")
        assertContains(entry.message, "\"networkId\":\"network-id\"")
    }

    private class RecordingLogWriter : LogWriter() {
        val entries = mutableListOf<Entry>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            entries += Entry(severity, message)
        }
    }

    private data class Entry(
        val severity: Severity,
        val message: String,
    )
}
