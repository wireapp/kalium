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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppleSuspensionDiagnosticsTest {
    @Test
    fun descriptorReuseBeforeCloseCallbackCannotLeaveTheOldAcquisitionHeld() {
        val lines = mutableListOf<String>()
        val registry = SuspensionDiagnosticRegistry(1, "test", { 1L }, lines::add)
        registry.setBackgrounded(true)
        val old = registry.beginLock("diagnostic_logging", "append_batch", 9)
        registry.acquired(old, 0, 0)
        // close(9) completed, but its callback has not yet reached the registry.
        val current = registry.beginLock("diagnostic_logging", "append_batch", 9)
        registry.acquired(current, 0, 0)
        registry.snapshot("TEST")
        assertTrue(lines.last { "event=snapshot " in it }.contains("held=1 unknown=1"))
        assertTrue(lines.last { "event=snapshot " in it }.contains("gaps=1"))
        assertTrue(lines.last { "event=outstanding " in it && "token=$old " in it }.contains("fdCurrent=false"))
        // Even a delayed successful acquisition callback must not resurrect the old fd owner.
        registry.acquired(old, 0, 0)
        registry.closed(old, 0, 0)
        registry.snapshot("TEST")
        assertTrue(lines.last { "event=snapshot " in it }.contains("held=1 unknown=0"))
        assertTrue(lines.last().contains("token=$current "))
        assertTrue(lines.last().contains("fdCurrent=true"))
    }

    @Test
    fun failedAcquireNeverClaimsHeldAndAbandonRetiresTheAttempt() {
        val fixture = Fixture()
        val token = fixture.registry.beginLock("account_runtime", "ownership", 7)
        fixture.registry.acquired(token, -1, 35)
        fixture.registry.snapshot("TEST")
        assertTrue(fixture.summary().contains("attempted=1 held=0 unknown=0"))
        assertTrue(fixture.lines.any { it.contains("event=acquired") && it.contains("state=attempted") && it.contains("errno=35") })
        fixture.registry.abandoned(token)
        fixture.registry.snapshot("TEST")
        assertTrue(fixture.summary().contains("outstanding=0"))
    }

    @Test
    fun failedUnlockAndInterruptedCloseStayUnknownAcrossDescriptorReuse() {
        val fixture = Fixture()
        val old = fixture.registry.beginLock("diagnostic_logging", "append_batch", 7)
        fixture.registry.acquired(old, 0, 0)
        fixture.registry.unlocked(old, -1, 4)
        fixture.registry.acquired(old, -1, 35)
        fixture.registry.abandoned(old) // Must not erase a possibly held lock.
        fixture.registry.closed(old, -1, 4)
        val reused = fixture.registry.beginLock("diagnostic_logging", "append_batch", 7)
        assertNotEquals(old, reused)
        fixture.registry.acquired(reused, 0, 0)
        fixture.registry.closed(reused, 0, 0)
        fixture.registry.snapshot("TEST")
        assertTrue(fixture.summary().contains("outstanding=1 attempted=0 held=0 unknown=1"))
        assertTrue(fixture.lines.last().contains("token=$old"))
        assertTrue(fixture.lines.last().contains("state=unknown"))
        assertTrue(fixture.summary().contains("gaps=2"))
    }

    @Test
    fun successfulUnlockRetiresOnlyThatAcquisition() {
        val fixture = Fixture()
        val first = fixture.registry.beginLock("diagnostic_logging", "append_batch", 9)
        fixture.registry.acquired(first, 0, 0)
        fixture.registry.unlocked(first, 0, 0)
        val second = fixture.registry.beginLock("diagnostic_logging", "append_batch", 9)
        fixture.registry.acquired(second, 0, 0)
        fixture.registry.closed(first, 0, 0) // Late observation for old identity must not clear the new one.
        fixture.registry.snapshot("TEST")
        assertTrue(fixture.summary().contains("outstanding=1 attempted=0 held=1 unknown=0"))
        assertTrue(fixture.summary().contains("gaps=1"))
        assertTrue(fixture.lines.last().contains("token=$second"))
    }

    @Test
    fun boundedRegistryReportsOverflowAndDoesNotReuseTokens() {
        val fixture = Fixture(capacity = 2)
        val first = fixture.registry.beginLock("account_runtime", "ownership", 1)
        val second = fixture.registry.beginLock("account_runtime", "ownership", 2)
        val overflow = fixture.registry.beginLock("account_runtime", "ownership", 3)
        assertEquals(0L, overflow)
        fixture.registry.acquired(overflow, 0, 0)
        fixture.registry.closed(overflow, 0, 0)
        fixture.registry.closed(first, 0, 0)
        val next = fixture.registry.beginLock("account_runtime", "ownership", 1)
        assertTrue(next > second)
        fixture.registry.snapshot("TEST")
        assertTrue(fixture.summary().contains("outstanding=2"))
        assertTrue(fixture.summary().contains("gaps=0 overflow=1"))
    }

    @Test
    fun quietForegroundStillTracksOutstandingOperationsAndLongCompletions() {
        val fixture = Fixture()
        val read = fixture.registry.beginDatabaseOperation("user_database", "read")
        val global = fixture.registry.beginDatabaseOperation("global_database", "read")
        val transaction = fixture.registry.beginDatabaseOperation("user_database", "transaction")
        assertTrue(fixture.lines.isEmpty())
        fixture.registry.snapshot("BACKGROUND_REQUEST")
        assertTrue(fixture.summary().contains("userOperations=1 globalOperations=1 transactions=1"))
        assertTrue(fixture.summary().contains("sqliteVfsLocks=unobserved"))
        fixture.now += 1_001
        fixture.registry.endDatabaseOperation(read, "success")
        assertTrue(fixture.lines.last().contains("event=database_end"))
        assertTrue(fixture.lines.last().contains("ageMs=1001"))
        fixture.registry.endDatabaseOperation(global, "success")
        fixture.registry.endDatabaseOperation(transaction, "commit")
    }

    @Test
    fun throttledEmissionsAreReportedAndDoNotSuppressSnapshots() {
        val fixture = Fixture(limit = 2)
        fixture.registry.setBackgrounded(true)
        val token = fixture.registry.beginLock("diagnostic_logging", "append_batch", 7)
        fixture.registry.acquired(token, 0, 0)
        fixture.registry.unlocked(token, 0, 0)
        fixture.registry.snapshot("BACKGROUND_TASK_FINISH")
        assertEquals(3, fixture.lines.size)
        assertTrue(fixture.summary().contains("throttled=1"))
        assertTrue(fixture.summary().contains("outstanding=0"))
    }

    @Test
    fun labelsCannotPublishSqlPathsOrUserIdentifiers() {
        val fixture = Fixture()
        fixture.registry.setBackgrounded(true)
        val token = fixture.registry.beginDatabaseOperation("secret@example.com", "SELECT secret FROM private")
        fixture.registry.endDatabaseOperation(token, "/private/secret")
        fixture.registry.snapshot("secret@example.com")
        assertTrue(fixture.lines.any { it.contains("kind=other operation=other") })
        assertTrue(fixture.summary().contains("stage=other"))
        assertFalse(fixture.lines.joinToString().contains("secret"))
    }

    @Test
    fun emitterRunsOutsideRegistryLockAndItsFailureIsCounted() {
        val lines = mutableListOf<String>()
        var first = true
        lateinit var registry: SuspensionDiagnosticRegistry
        registry = SuspensionDiagnosticRegistry(1, "test-run", { 1_000 }, emit = { line ->
            if (first) {
                first = false
                registry.snapshot("TEST") // Re-entry would deadlock if emission held the registry lock.
                error("unavailable unified log")
            }
            lines += line
        })
        registry.setBackgrounded(true)
        registry.beginLock("account_runtime", "ownership", 7)
        registry.snapshot("TEST")
        assertTrue(lines.last { it.contains("event=snapshot") }.contains("emitFailures=1"))
    }

    @Test
    fun concurrentAcquisitionsHaveUniqueIdentitiesAndMonotonicSequenceTimes() = runTest {
        val lines = mutableListOf<String>()
        val outputLock = NSLock()
        var now = 10_000L
        val registry = SuspensionDiagnosticRegistry(42, "test-run", { ++now }, emit = { line ->
            outputLock.lock()
            try { lines += line } finally { outputLock.unlock() }
        }, eventLimitPerSecond = 1_024)
        registry.setBackgrounded(true)
        (1..100).map { descriptor ->
            async(Dispatchers.Default) {
                val token = registry.beginLock("diagnostic_logging", "append_batch", descriptor)
                registry.acquired(token, 0, 0)
                registry.unlocked(token, 0, 0)
            }
        }.awaitAll()
        registry.snapshot("TEST")
        val sequences = lines.map { it.substringAfter("seq=").substringBefore(' ').toLong() }
        assertEquals(sequences.size, sequences.toSet().size)
        val observations = lines.sortedBy { it.substringAfter("seq=").substringBefore(' ').toLong() }
        val times = observations.map { it.substringAfter("monoMs=").substringBefore(' ').toLong() }
        assertEquals(times.sorted(), times)
        assertEquals(100, lines.filter { it.contains("event=begin ") }
            .map { it.substringAfter("token=").substringBefore(' ') }.toSet().size)
        assertTrue(lines.last { it.contains("event=snapshot") }.contains("outstanding=0"))
    }

    private class Fixture(capacity: Int = 256, limit: Int = 64) {
        var now = 10_000L
        val lines = mutableListOf<String>()
        val registry = SuspensionDiagnosticRegistry(42, "test-run", { now }, { lines += it }, capacity, limit)
        fun summary(): String = lines.last { it.contains("event=snapshot") }
    }
}
