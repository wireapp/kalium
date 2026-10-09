/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.sync.receiving.CallPresentationRecord
import com.wire.kalium.logic.sync.receiving.CallSignalRecord
import com.wire.kalium.logic.sync.receiving.CallSignalStore
import com.wire.kalium.logic.sync.receiving.IncomingCallReference
import com.wire.kalium.logic.sync.receiving.ReceivedCallEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CallSignalReplayTest {
    @Test
    fun priorityDoesNotSubmitUnrelatedHistory() = runTest {
        val store = Store((0..294).map { signal("old-$it", "other", it.toLong()) } + signal("incoming", "current", 300))
        val submitted = mutableListOf<String>()
        val replay = CallSignalReplay(store, "handle", { submitted += it.id }, {})
        replay.replayIncoming("incoming")
        assertEquals(listOf("incoming"), submitted)
        assertEquals(295, store.records.count { it.submittedGeneration == null })
    }

    @Test
    fun foregroundRequestPreemptsAnExistingDrainBetweenSignals() = runTest {
        val store = Store(listOf(signal("slow", "other", 1), signal("later", "other", 2), signal("incoming", "current", 3)))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val submitted = mutableListOf<String>()
        val replay = CallSignalReplay(store, "handle", {
            submitted += it.id
            if (it.id == "slow") { started.complete(Unit); release.await() }
        }, {})
        val background = async { replay.replay() }
        started.await()
        val incoming = async { replay.replayIncoming("incoming") }
        runCurrent()
        assertFalse(incoming.isCompleted)
        release.complete(Unit)
        incoming.await()
        background.await()
        assertEquals(listOf("slow", "incoming", "later"), submitted)
    }

    @Test
    fun fullConversationOrderIncludesHistoryAndTerminalSignals() = runTest {
        val records = listOf(
            signal("old-start", "current", 1), signal("old-cancel", "current", 2, "CANCEL"),
            signal("unrelated", "other", 3), signal("incoming", "current", 4),
            signal("reject", "current", 5, "REJECT"), signal("cancel", "current", 6, "CANCEL"),
        )
        val store = Store(records)
        val submitted = mutableListOf<String>()
        val replay = CallSignalReplay(store, "handle", { submitted += it.id }, {})
        replay.replayIncoming("incoming")
        replay.replayIncoming("incoming")
        assertEquals(listOf("old-start", "old-cancel", "incoming", "reject", "cancel"), submitted)
        replay.replay()
        assertEquals("unrelated", submitted.last())
    }

    @Test
    fun watermarkPreventsDelayedOldSignalsFromRegressingPriorityCall() = runTest {
        val store = Store(listOf(signal("incoming", "current", 10)))
        val submitted = mutableListOf<String>()
        val stale = mutableListOf<String>()
        val replay = CallSignalReplay(store, "handle", { submitted += it.id }, { stale += it.id })
        replay.replayIncoming("incoming")
        store.records += signal("old-reject", "current", 9, "REJECT")
        store.records += signal("same-time-cancel", "current", 10, "CANCEL")
        replay.replay()
        assertEquals(listOf("incoming", "same-time-cancel"), submitted)
        assertEquals(listOf("old-reject"), stale)
        assertTrue(store.records.all { it.submittedGeneration == "handle" })
    }

    @Test
    fun failedSubmissionCanRetryAndDoesNotLeakPriority() = runTest {
        val store = Store(listOf(signal("old", "other", 1), signal("incoming", "current", 2)))
        var fail = true
        val submitted = mutableListOf<String>()
        val replay = CallSignalReplay(store, "handle", {
            if (fail) error("native refused")
            submitted += it.id
        }, {})
        assertFailsWith<IllegalStateException> { replay.replayIncoming("incoming") }
        assertTrue(store.records.all { it.submittedGeneration == null })
        fail = false
        replay.replay()
        assertEquals(listOf("old", "incoming"), submitted)
    }

    @Test
    fun cancellationReleasesPriorityWithoutAcknowledgingFailedEnqueue() = runTest {
        val store = Store(listOf(signal("old", "other", 1), signal("incoming", "current", 2)))
        val started = CompletableDeferred<Unit>()
        val block = CompletableDeferred<Unit>()
        var pause = true
        val submitted = mutableListOf<String>()
        val replay = CallSignalReplay(store, "handle", {
            if (pause) { started.complete(Unit); block.await() }
            submitted += it.id
        }, {})
        val priority = async { replay.replayIncoming("incoming") }
        started.await()
        priority.cancelAndJoin()
        pause = false
        replay.replay()
        assertEquals(listOf("old", "incoming"), submitted)
    }

    @Test
    fun failedAcknowledgementNeverRepeatsAcceptedNativeEnqueue() = runTest {
        val store = Store(listOf(signal("incoming", "current", 1)))
        store.failAcknowledgement = true
        val submitted = mutableListOf<String>()
        val replay = CallSignalReplay(store, "handle", { submitted += it.id }, {})
        assertFailsWith<IllegalStateException> { replay.replayIncoming("incoming") }
        store.failAcknowledgement = false
        replay.replayIncoming("incoming")
        assertEquals(listOf("incoming"), submitted)
        assertEquals("handle", store.records.single().submittedGeneration)
        // Another native generation must still replay history retained after an ack failure.
        CallSignalReplay(store, "new-handle", { submitted += it.id }, {}).replay()
        assertEquals(listOf("incoming", "incoming"), submitted)
    }

    @Test
    fun cancellationAfterAcceptanceFinishesTheDurableAcknowledgement() = runTest {
        val store = Store(listOf(signal("incoming", "current", 1)))
        val acknowledging = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        store.beforeAcknowledgement = { acknowledging.complete(Unit); release.await() }
        val submitted = mutableListOf<String>()
        val replay = CallSignalReplay(store, "handle", { submitted += it.id }, {})
        val priority = async { replay.replayIncoming("incoming") }
        acknowledging.await()
        priority.cancel()
        runCurrent()
        assertFalse(priority.isCompleted)
        release.complete(Unit)
        priority.join()
        store.beforeAcknowledgement = {}
        replay.replayIncoming("incoming")
        assertEquals(listOf("incoming"), submitted)
        assertEquals("handle", store.records.single().submittedGeneration)
    }

    @Test
    fun priorityRequiresAnExactReadyStartSignal() = runTest {
        val store = Store(listOf(signal("terminal", "current", 1, "REJECT"), signal("unready", "current", 2).copy(ready = false)))
        val replay = CallSignalReplay(store, "handle", { error("must not submit") }, {})
        assertFailsWith<IllegalStateException> { replay.replayIncoming("missing") }
        assertFailsWith<IllegalStateException> { replay.replayIncoming("terminal") }
        assertFailsWith<IllegalStateException> { replay.replayIncoming("unready") }
    }

    @Test
    fun newerDistinctStartCannotBeAnsweredBeforeItsCallbackArrives() = runTest {
        val incoming = signal("incoming", "current", 1)
        val newer = signal("newer", "current", 2).copy(payload = """{"type":"SETUP","resp":false,"sessid":"new-call"}""")
        val store = Store(listOf(incoming, newer))
        val submitted = mutableListOf<String>()
        val replay = CallSignalReplay(store, "handle", { submitted += it.id }, {})
        assertFailsWith<IllegalStateException> { replay.replayIncoming("incoming") }
        assertEquals(listOf("incoming", "newer"), submitted)
        replay.replayIncoming("newer")
        assertEquals(listOf("incoming", "newer"), submitted)
    }

    @Test
    fun outOfOrderOldStartCannotAnswerTheNewerNativeCall() = runTest {
        val old = signal("old", "current", 1)
        val newer = signal("newer", "current", 2).copy(payload = """{"type":"SETUP","resp":false,"sessid":"new-call"}""")
        val store = Store(listOf(newer, old))
        val submitted = mutableListOf<String>()
        val stale = mutableListOf<String>()
        val replay = CallSignalReplay(store, "handle", { submitted += it.id }, { stale += it.id })
        assertFailsWith<IllegalStateException> { replay.replayIncoming("old") }
        assertEquals(listOf("newer"), submitted)
        assertEquals(listOf("old"), stale)
    }

    @Test
    fun repeatedConferenceBroadcastDoesNotSupersedeTheCall() = runTest {
        val incoming = signal("incoming", "current", 1, "CONFSTART").copy(isGroup = true)
        val repeated = signal("repeat", "current", 2, "CONFSTART").copy(isGroup = true, senderValue = "other-participant")
        val store = Store(listOf(incoming, repeated))
        val submitted = mutableListOf<String>()
        CallSignalReplay(store, "handle", { submitted += it.id }, {}).replayIncoming("incoming")
        assertEquals(listOf("incoming", "repeat"), submitted)
    }

    @Test
    fun competingConferenceBroadcastsRemainAvailableForNativeArbitration() = runTest {
        val incoming = signal("incoming", "current", 1, "CONFSTART").copy(isGroup = true)
        val newer = signal("newer", "current", 2, "CONFSTART").copy(
            isGroup = true, payload = incoming.payload.replace("\"timestamp\":\"1\"", "\"timestamp\":\"2\""),
        )
        val store = Store(listOf(incoming, newer))
        val submitted = mutableListOf<String>()
        val replay = CallSignalReplay(store, "handle", { submitted += it.id }, {})
        replay.replayIncoming("incoming")
        replay.replayIncoming("newer")
        assertEquals(listOf("incoming", "newer"), submitted)
    }

    @Test
    fun legacyGroupStartsWithoutSessionIdentityRemainInNativeOrder() = runTest {
        val incoming = signal("incoming", "current", 1, "GROUPSTART").copy(
            isGroup = true, payload = """{"type":"GROUPSTART","resp":false}""",
        )
        val later = incoming.copy(id = "later", messageId = "later", timeMillis = 2)
        val store = Store(listOf(incoming, later))
        val submitted = mutableListOf<String>()
        val replay = CallSignalReplay(store, "handle", { submitted += it.id }, {})
        replay.replayIncoming("incoming")
        replay.replayIncoming("later")
        assertEquals(listOf("incoming", "later"), submitted)
    }

    private fun signal(id: String, conversation: String, time: Long, type: String = "SETUP") = CallSignalRecord(
        id, id, conversation, "wire.test", "caller", "wire.test", "client",
        """{"type":"$type","resp":false,"sessid":"call","timestamp":"1","seqno":"1","secret":"YWJjZA==","sft_url":"https://sft.invalid"}""",
        time, false, false, time, ready = true,
    )

    private class Store(records: List<CallSignalRecord>) : CallSignalStore {
        var records = records
        var failAcknowledgement = false
        var beforeAcknowledgement: suspend () -> Unit = {}
        override suspend fun signals() = records.filter { it.ready }
        override suspend fun presentations() = emptyList<CallPresentationRecord>()
        override suspend fun append(event: ReceivedCallEvent): CallSignalRecord = error("unused")
        override suspend fun submitted(id: String, generation: String) {
            beforeAcknowledgement()
            check(!failAcknowledgement) { "database unavailable" }
            records = records.map { if (it.id == id) it.copy(submittedGeneration = generation) else it }
        }
        override suspend fun incoming(reference: IncomingCallReference, ring: Boolean, video: Boolean, generation: String) =
            null
        override suspend fun closed(conversationValue: String, conversationDomain: String, reason: Int): String? = null
    }
}
