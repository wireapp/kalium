/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleCallTraceTest {
    @Test
    fun emitsOnlyAllowlistedFieldsAndOmitsInvalidIdentifiers() {
        val lines = mutableListOf<String>()
        val process = CallTraceProcess()
        process.configure(CallTraceRole.NSE) { lines += it }
        val signal = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"
        process.record(CallTraceEvent.NSE_INCOMING,
            CallTraceContext(signal = signal, attempt = "name@example.com\nsecret"), null, null, true)
        assertTrue(lines.single().contains("role=NSE seq=1 event=NSE_INCOMING signal=${signal.lowercase()} flag=true"))
        assertFalse(lines.single().contains("secret"))
        assertFalse(lines.single().contains("attempt="))
    }

    @Test
    fun recordsFixedOriginAndDistinctActionOperation() {
        var line = ""
        val process = CallTraceProcess()
        process.configure(CallTraceRole.APP) { line = it }
        val operation = "11111111-2222-3333-4444-555555555555"
        process.record(CallTraceEvent.TRANSACTION_MUTE, CallTraceContext(operation = operation),
            CallTraceResult.ACCEPTED, null, true, CallTraceOrigin.INITIAL_SYNC)
        assertTrue(line.contains("operation=$operation origin=INITIAL_SYNC result=ACCEPTED flag=true"))
    }

    @Test
    fun concurrentObservationsHaveUniqueSequenceAndStableBoot() = runTest {
        val lines = mutableListOf<String>()
        val lock = NSLock()
        val process = CallTraceProcess()
        process.configure(CallTraceRole.APP) {
            lock.lock()
            try { lines += it } finally { lock.unlock() }
        }
        (1..100).map {
            async(Dispatchers.Default) {
                process.record(CallTraceEvent.START, CallTraceContext(), CallTraceResult.REQUESTED, null, null)
            }
        }.awaitAll()
        assertEquals((1..100).toSet(), lines.map { it.substringAfter("seq=").substringBefore(' ').toInt() }.toSet())
        assertEquals(1, lines.map { it.substringAfter("boot=").substringBefore(' ') }.toSet().size)
    }

    @Test
    fun sinkFailureDoesNotEscapeAndReconfigurationKeepsSequence() {
        val process = CallTraceProcess()
        process.configure(CallTraceRole.APP) { error("sink failure") }
        process.record(CallTraceEvent.END, CallTraceContext(), CallTraceResult.FAILED, null, null)
        var line = ""
        process.configure(CallTraceRole.APP) { line = it }
        process.record(CallTraceEvent.END, CallTraceContext(), CallTraceResult.COMPLETED, null, null)
        assertTrue(line.contains("seq=2"))
    }
}
