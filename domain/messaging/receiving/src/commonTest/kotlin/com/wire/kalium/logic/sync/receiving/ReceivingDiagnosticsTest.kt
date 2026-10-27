/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.serverMiscommunication

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.ServerError
import com.wire.kalium.common.logger.CoreLogger
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceivingDiagnosticsTest {
    @Test
    fun normalizedFailureWithoutTransportCauseKeepsSafeDiagnostics() {
        val diagnostic = ReceivingDiagnostics()
        diagnostic.failed(KaliumSyncException("private message", NetworkFailure.ServerMiscommunication(
            ServerError.InvalidRequest(ServerError.Response(404, "private body", "no-conversation"))
        )))

        assertEquals(404, diagnostic.httpStatus)
        assertEquals("NO_CONVERSATION", diagnostic.networkError)
        assertEquals("ServerMiscommunication", diagnostic.errorCode)
    }

    @Test
    fun configuredWriterReceivesSafeStructuredFailure() {
        val writer = RecordingWriter()
        CoreLogger.init(KaliumLogger.Config(KaliumLogLevel.INFO, listOf(writer)))
        try {
            val diagnostic = ReceivingDiagnostics()
            diagnostic.beginEvent(false, "private-event-id")
            diagnostic.decoding(EventContentDTO.Unknown("secret-server-event"))
            diagnostic.failed(
                KaliumSyncException(
                    "secret-message-content",
                    serverMiscommunication(
                        KaliumException.InvalidRequestError(GenericAPIErrorResponse(403, "secret-body", "secret-server-label"))
                    )
                )
            )
            diagnostic.record("FAILED", "secret-result-reason", false)

            val (severity, message, tag) = writer.entries.single()
            assertEquals(Severity.Error, severity)
            assertTrue(tag.contains("event_receiver"))
            assertTrue(message.contains("\"outcome\":\"failure\""))
            assertTrue(message.contains("\"event\":\"UNKNOWN_EVENT\""))
            assertTrue(message.contains("\"position\":\"EARLIER\""))
            assertTrue(message.contains("\"httpStatus\":403"))
            assertTrue(message.contains("\"networkError\":\"OTHER\""))
            assertTrue(message.contains("\"eventHash\":\"${diagnostic.eventHash}\""))
            assertEquals(24, diagnostic.eventHash?.length)
            assertFalse(message.contains("private-event-id"))
            assertFalse(message.contains("secret-"))
        } finally {
            CoreLogger.init(KaliumLogger.Config.DISABLED)
        }
    }

    @Test
    fun serverAndRedirectResponsesKeepStatusWithoutServerText() {
        val writer = RecordingWriter()
        CoreLogger.init(KaliumLogger.Config(KaliumLogLevel.INFO, listOf(writer)))
        try {
            val failures = listOf(
                KaliumException.ServerError(GenericAPIErrorResponse(503, "secret-body", "secret-label")),
                KaliumException.RedirectError(GenericAPIErrorResponse(302, "secret-body", "secret-label")),
            )
            failures.forEach { failure ->
                val diagnostic = ReceivingDiagnostics()
                diagnostic.failed(KaliumSyncException("secret-message", serverMiscommunication(failure)))
                diagnostic.record("FAILED", null, false)
            }
            assertEquals(2, writer.entries.size)
            assertTrue(writer.entries[0].second.contains("\"httpStatus\":503"))
            assertTrue(writer.entries[1].second.contains("\"httpStatus\":302"))
            writer.entries.forEach { (_, message, _) ->
                assertTrue(message.contains("\"networkError\":\"OTHER\""))
                assertFalse(message.contains("secret-"))
            }
        } finally {
            CoreLogger.init(KaliumLogger.Config.DISABLED)
        }
    }

    @Test
    fun disabledConfigEmitsNothingAndFetchingClearsEventContext() {
        val writer = RecordingWriter()
        CoreLogger.init(KaliumLogger.Config(KaliumLogLevel.DISABLED, listOf(writer)))
        try {
            val diagnostic = ReceivingDiagnostics()
            diagnostic.beginEvent(true, "private-event-id")
            diagnostic.failed(IllegalStateException("private exception message"))
            diagnostic.fetching()
            assertEquals(null, diagnostic.eventHash)
            assertEquals(null, diagnostic.errorCode)
            assertEquals(null, diagnostic.failureType)
            assertEquals(null, diagnostic.isTarget)
            diagnostic.record("FAILED", "TIMEOUT", false)
            assertTrue(writer.entries.isEmpty())
        } finally {
            CoreLogger.init(KaliumLogger.Config.DISABLED)
        }
    }

    @Test
    fun wrappedMlsFailureKeepsCodeWithoutMessage() {
        val diagnostic = ReceivingDiagnostics()
        diagnostic.failed(IllegalStateException("secret-wrapper", KaliumSyncException("secret-core", MLSFailure.CommitForMissingProposal)))
        assertEquals("CommitForMissingProposal", diagnostic.errorCode)
        assertEquals("IllegalStateException/KaliumSyncException/CommitForMissingProposal", diagnostic.failureType)
    }

    @Test
    fun failingHostWriterDoesNotChangeReceivingResult() {
        val writer = object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                error("host writer failed")
            }
        }
        CoreLogger.init(KaliumLogger.Config(KaliumLogLevel.INFO, listOf(writer)))
        try {
            ReceivingDiagnostics().record("SUCCESS", null, true)
        } finally {
            CoreLogger.init(KaliumLogger.Config.DISABLED)
        }
    }

    private class RecordingWriter : LogWriter() {
        val entries = mutableListOf<Triple<Severity, String, String>>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            entries += Triple(severity, message, tag)
        }
    }
}
