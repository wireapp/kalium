/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.withTestUserDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CallSignalJournalTest {

    @Test
    fun tiedStartsKeepOneIdentityAcrossAdaptersAndEngineGenerations() = runTest {
        withJournal { journal, _ ->
            val first = journal.append(start("first"))
            journal.append(start("second").copy(time = CALL_TIME + 500.milliseconds))

            assertEquals(first.id, journal.incomingSource(reference())?.id)
            assertTrue(journal.presentations().isEmpty()) // NSE lookup does not publish live call state.
            assertTrue(journal.signals().all { it.incomingGeneration == null })

            val presentation = checkNotNull(journal.incoming(reference(), true, false, "engine-one"))
            assertEquals(first.id, presentation.id)
            journal.submitted(first.id, "engine-one")
            repeat(2) {
                assertEquals(presentation, journal.incoming(reference(), true, false, "engine-one"))
                assertEquals(first.id, journal.incomingSource(reference())?.id)
            }
            assertEquals(presentation, journal.incoming(reference(), true, false, "engine-two"))
            assertEquals(listOf(presentation), journal.presentations())
        }
    }

    @Test
    fun terminalPresentationCannotAdvanceToATiedStartOnReplay() = runTest {
        withJournal { journal, database ->
            val first = journal.append(start("first"))
            journal.append(start("second"))
            journal.incoming(reference(), true, true, "engine-one")
            assertEquals(first.id, journal.closed(CONVERSATION.value, CONVERSATION.domain, 5))

            val reopened = CallSignalJournal(database.builder)
            for (generation in listOf("engine-one", "engine-two")) {
                assertEquals(first.id, reopened.incomingSource(reference())?.id)
                val replay = checkNotNull(reopened.incoming(reference(), true, true, generation))
                assertEquals(first.id, replay.id)
                assertFalse(replay.ringing)
                assertEquals(5, replay.endReason)
            }
            assertEquals(1, reopened.presentations().size)
        }
    }

    @Test
    fun fullEngineReplayClosesTheOldCallBeforeRestoringTheNewerPresentation() = runTest {
        withJournal { journal, database ->
            val first = journal.append(start("first"))
            journal.incoming(reference(), true, false, "engine-one")
            journal.closed(CONVERSATION.value, CONVERSATION.domain, 5)
            val nextTime = CALL_TIME + 2.seconds
            val next = journal.append(start("next").copy(time = nextTime))
            val nextReference = reference().copy(messageTimeSeconds = nextTime.epochSeconds)
            journal.incoming(nextReference, true, false, "engine-one")

            val reopened = CallSignalJournal(database.builder)
            assertFalse(checkNotNull(reopened.incoming(reference(), true, false, "engine-two")).ringing)
            assertEquals(first.id, reopened.closed(CONVERSATION.value, CONVERSATION.domain, 5))
            assertTrue(checkNotNull(reopened.incoming(nextReference, true, false, "engine-two")).ringing)
            assertEquals(next.id, reopened.presentations().last().id)
        }
    }

    @Test
    fun incomingIgnoresUnreadyStartsUntilTheirNativeCommit() = runTest {
        withJournal { journal, database ->
            CallSignalJournal.append(database.builder.metadataDAO, start("unready"))
            assertNull(journal.incomingSource(reference()))
            assertNull(journal.incoming(reference(), true, false, "engine"))

            val ready = journal.append(start("ready"))
            assertEquals(ready.id, journal.incomingSource(reference())?.id)
            assertEquals(ready.id, journal.incoming(reference(), true, false, "engine")?.id)
        }
    }

    @Test
    fun callerClientEvidenceDiscriminatesTiedStarts() = runTest {
        withJournal { journal, _ ->
            val first = journal.append(start("first", "client-one"))
            val second = journal.append(start("second", "client-two"))
            assertEquals(second.id, journal.incomingSource(reference("client-two"))?.id)
            assertEquals(second.id, journal.incoming(reference("client-two"), true, false, "engine")?.id)
            assertEquals(first.id, journal.incomingSource(reference(null))?.id)
            assertEquals(first.id, journal.incoming(reference(null), true, false, "engine")?.id)
            assertNull(journal.incomingSource(reference("unknown-client")))
            assertNull(journal.incoming(reference("unknown-client"), true, false, "engine"))
        }
    }

    @Test
    fun onlyStartRequestsCanSupplyIncomingIdentity() = runTest {
        withJournal { journal, _ ->
            for ((index, payload) in listOf(
                "not json", "{}", """{"type":"CONFEND","resp":false}""",
                """{"type":"SETUP","resp":true}""", """{"type":"SETUP"}""",
            ).withIndex()) {
                journal.append(start("ignored-$index", payload = payload))
            }
            assertNull(journal.incomingSource(reference()))
            val request = journal.append(start("request"))
            journal.append(start("response", payload = """{"type":"SETUP","resp":true}"""))
            assertEquals(request.id, journal.incomingSource(reference())?.id)
            assertEquals(request.id, journal.incoming(reference(), true, false, "engine")?.id)
        }
    }

    @Test
    fun allNativeStartTypesUseTheSharedDecision() = runTest {
        withJournal { journal, _ ->
            for ((index, type) in listOf("SETUP", "GROUPSTART", "CONFSTART", "setup").withIndex()) {
                val time = CALL_TIME + index.seconds
                val source = journal.append(start(type, payload = """{"type":"$type","resp":false}""").copy(time = time))
                val callback = reference().copy(messageTimeSeconds = time.epochSeconds)
                assertEquals(source.id, journal.incomingSource(callback)?.id)
                assertEquals(source.id, journal.incoming(callback, true, false, "engine")?.id)
            }
        }
    }

    @Test
    fun unmatchedCallbackCannotReuseAnotherPresentation() = runTest {
        withJournal { journal, _ ->
            journal.append(start("first"))
            val presentation = checkNotNull(journal.incoming(reference(), true, false, "engine"))
            for (callback in listOf(
                reference().copy(conversation = CONVERSATION.copy(value = "other")),
                reference().copy(conversation = CONVERSATION.copy(domain = "other.example")),
                reference().copy(caller = SENDER.copy(value = "other")),
                reference().copy(caller = SENDER.copy(domain = "other.example")),
                reference().copy(messageTimeSeconds = CALL_TIME.epochSeconds + 1),
            )) {
                assertNull(journal.incomingSource(callback))
                assertNull(journal.incoming(callback, false, false, "engine"))
            }
            assertEquals(listOf(presentation), journal.presentations())
        }
    }

    @Test
    fun nonRingingCallbackUpdatesOnlyItsMatchingPresentation() = runTest {
        withJournal { journal, _ ->
            val first = journal.append(start("first"))
            val nextTime = CALL_TIME + 2.seconds
            val next = journal.append(start("next").copy(time = nextTime))
            journal.incoming(reference(), true, false, "engine")
            journal.incoming(reference().copy(messageTimeSeconds = nextTime.epochSeconds), true, false, "engine")

            val quiet = checkNotNull(journal.incoming(reference(), false, false, "engine"))
            assertEquals(first.id, quiet.id)
            assertFalse(quiet.ringing)
            assertTrue(journal.presentations().single { it.id == next.id }.ringing)
        }
    }

    @Test
    fun givenFullJournalOfUnsubmittedSignals_whenAppending_thenOldestSignalIsDropped() = runTest {
        val database = TestUserDatabase(UserIDEntity("call-journal", "example.com"), StandardTestDispatcher(testScheduler))
        try {
            val metadata = database.builder.metadataDAO
            val now = Clock.System.now().toEpochMilliseconds()
            val stored = List(CallSignalJournal.MAX_SIGNALS) { record("stored-$it", now) }
            metadata.putSerializable(CallSignalJournal.KEY, CallSignalJournalState(stored), CallSignalJournalState.serializer())

            val appended = CallSignalJournal.append(metadata, event("new-message"))

            val signals = checkNotNull(metadata.getSerializable(CallSignalJournal.KEY, CallSignalJournalState.serializer())).signals
            assertEquals(CallSignalJournal.MAX_SIGNALS, signals.size)
            assertEquals("stored-1", signals.first().id)
            assertEquals(appended.id, signals.last().id)
        } finally {
            database.delete()
        }
    }

    @Test
    fun expiredSignalsAreNeitherReplayedNorKept() = runTest {
        withJournal { journal, database ->
            val metadata = database.builder.metadataDAO
            val stored = Clock.System.now().toEpochMilliseconds() - CallSignalJournal.SIGNAL_RETENTION_MS
            val expired = record("expired", stored).copy(ready = true)
            metadata.putSerializable(CallSignalJournal.KEY, CallSignalJournalState(listOf(expired)), CallSignalJournalState.serializer())
            assertTrue(journal.signals().isEmpty())

            val fresh = journal.append(start("fresh"))
            val kept = checkNotNull(metadata.getSerializable(CallSignalJournal.KEY, CallSignalJournalState.serializer())).signals
            assertEquals(listOf(fresh.id), kept.map { it.id })
        }
    }

    @Test
    fun closingAnEndedCallForgetsOnlyTheSignalsItsEngineHandled() = runTest {
        withJournal { journal, _ ->
            val handled = journal.append(start("handled"))
            journal.submitted(handled.id, "engine")
            val pending = journal.append(start("pending").copy(time = CALL_TIME + 1.seconds))

            journal.closed(CONVERSATION.value, CONVERSATION.domain, 5)
            assertEquals(2, journal.signals().size)
            journal.closed(CONVERSATION.value, CONVERSATION.domain, 5, handledBy = "engine")
            assertEquals(listOf(pending.id), journal.signals().map { it.id })
        }
    }

    @Test
    fun acceptedCancellationBeforeIncomingCallbackPersistsTerminalPresentation() = runTest {
        withJournal { journal, database ->
            val source = journal.append(start("fast"))
            val cancel = journal.append(cancellation("fast"))
            assertTrue(journal.presentations().isEmpty())
            journal.submitted(source.id, "engine")
            journal.submitted(cancel.id, "engine")

            val ended = journal.presentations().single()
            assertEquals(source.id, ended.id)
            assertFalse(ended.ringing)
            assertEquals(4, ended.endReason)
            val reopened = CallSignalJournal(database.builder)
            assertEquals(ended, reopened.incoming(reference(), true, false, "next-engine"))
            reopened.submitted(cancel.id, "next-engine")
            assertEquals(listOf(ended), reopened.presentations())
        }
    }

    @Test
    fun cancellationDoesNotReplaceNativeStateOrStealLaterCallsClose() = runTest {
        withJournal { journal, _ ->
            val old = journal.append(start("old"))
            val newerTime = CALL_TIME + 2.seconds
            val newer = journal.append(start("newer").copy(time = newerTime))
            val live = journal.incoming(reference().copy(messageTimeSeconds = newerTime.epochSeconds), true, false, "engine")
            val cancel = journal.append(cancellation("old"))
            journal.submitted(old.id, "engine")
            journal.submitted(cancel.id, "engine")
            assertEquals(live, journal.presentations().last())
            assertEquals(newer.id, journal.closed(CONVERSATION.value, CONVERSATION.domain, 5))
            assertEquals(4, journal.presentations().single { it.id == old.id }.endReason)
        }
        withJournal { journal, _ ->
            val source = journal.append(start("active"))
            val live = journal.incoming(reference(), false, false, "engine")
            val cancel = journal.append(cancellation("active"))
            journal.submitted(source.id, "engine")
            journal.submitted(cancel.id, "engine")
            assertEquals(listOf(live), journal.presentations())
        }
    }

    @Test
    fun cancellationRequiresUnambiguousAcceptedOneToOneSessionEvidence() = runTest {
        val base = cancellation("source")
        val mismatches = listOf(
            base.copy(conversationId = CONVERSATION.copy(value = "other")),
            base.copy(conversationId = CONVERSATION.copy(domain = "other.example")),
            base.copy(senderId = SENDER.copy(value = "other")),
            base.copy(senderId = SENDER.copy(domain = "other.example")),
            base.copy(senderClientId = "other"),
            base.copy(time = CALL_TIME - 1.seconds),
            base.copy(isGroup = true),
            base.copy(payload = """{"type":"CANCEL","resp":true,"sessid":"source"}"""),
            base.copy(payload = """{"type":"REJECT","resp":false,"sessid":"source"}"""),
            base.copy(payload = """{"type":"CANCEL","resp":false,"sessid":"other"}"""),
            base.copy(payload = """{"type":"CANCEL","resp":false}"""),
            base.copy(payload = "not json"),
        )
        for (event in mismatches) withJournal { journal, _ ->
            val source = journal.append(start("source"))
            val cancel = journal.append(event)
            journal.submitted(source.id, "engine")
            journal.submitted(cancel.id, "engine")
            assertTrue(journal.presentations().isEmpty(), event.toString())
        }
        withJournal { journal, _ ->
            journal.append(start("source")) // Not yet accepted by this engine.
            val cancel = journal.append(base)
            journal.submitted(cancel.id, "engine")
            assertTrue(journal.presentations().isEmpty())
        }
        withJournal { journal, _ ->
            val source = journal.append(start("source"))
            val duplicate = journal.append(start("duplicate", payload = source.payload))
            val cancel = journal.append(base)
            journal.submitted(source.id, "engine")
            journal.submitted(duplicate.id, "engine")
            journal.submitted(cancel.id, "engine")
            assertTrue(journal.presentations().isEmpty())
        }
    }

    @Test
    fun cancellationPreservesExistingRingingAndEndedPresentations() = runTest {
        for (ended in listOf(false, true)) withJournal { journal, _ ->
            val source = journal.append(start("existing"))
            journal.incoming(reference(), true, false, "engine")
            if (ended) journal.closed(CONVERSATION.value, CONVERSATION.domain, 5)
            val original = journal.presentations()
            val cancel = journal.append(cancellation("existing"))
            journal.submitted(source.id, "engine")
            journal.submitted(cancel.id, "engine")
            assertEquals(original, journal.presentations())
        }
    }

    @Test
    fun cancellationIgnoresGroupSourcesOtherEnginesAndUnreadySignals() = runTest {
        withJournal { journal, _ ->
            val source = journal.append(start("source").copy(isGroup = true))
            val cancel = journal.append(cancellation("source"))
            journal.submitted(source.id, "engine")
            journal.submitted(cancel.id, "engine")
            assertTrue(journal.presentations().isEmpty())
        }
        withJournal { journal, _ ->
            val source = journal.append(start("source"))
            val cancel = journal.append(cancellation("source"))
            journal.submitted(source.id, "old-engine")
            journal.submitted(cancel.id, "new-engine")
            assertTrue(journal.presentations().isEmpty())
        }
        withJournal { journal, database ->
            val source = journal.append(start("source"))
            val cancel = CallSignalJournal.append(database.builder.metadataDAO, cancellation("source"))
            journal.submitted(source.id, "engine")
            journal.submitted(cancel.id, "engine")
            assertTrue(journal.presentations().isEmpty())
        }
        for (session in listOf("null", "42", "\"\"")) withJournal { journal, _ ->
            val source = journal.append(start("source", payload = """{"type":"SETUP","resp":false,"sessid":$session}"""))
            val cancel = journal.append(start("cancel", payload = """{"type":"CANCEL","resp":false,"sessid":$session}"""))
            journal.submitted(source.id, "engine")
            journal.submitted(cancel.id, "engine")
            assertTrue(journal.presentations().isEmpty())
        }
    }

    private fun cancellation(session: String) = start(
        "cancel-$session", payload = """{"type":"CANCEL","resp":false,"sessid":"$session"}""",
    ).copy(time = CALL_TIME + 500.milliseconds)

    private fun record(id: String, now: Long) = CallSignalRecord(
        id = id,
        messageId = id,
        conversationValue = CONVERSATION.value,
        conversationDomain = CONVERSATION.domain,
        senderValue = SENDER.value,
        senderDomain = SENDER.domain,
        senderClientId = "client",
        payload = "{}",
        timeMillis = now,
        isGroup = false,
        isMLS = false,
        storedAtMillis = now,
    )

    private fun event(messageId: String) = ReceivedCallEvent(
        payload = "{}",
        conversationId = CONVERSATION,
        senderId = SENDER,
        senderClientId = "client",
        time = Clock.System.now(),
        isGroup = false,
        isMLS = false,
        messageId = messageId,
    )

    private fun start(
        messageId: String,
        client: String = "client",
        payload: String = """{"type":"SETUP","resp":false,"sessid":"$messageId"}""",
    ) = event(messageId).copy(
        payload = payload,
        senderClientId = client,
        time = CALL_TIME,
    )

    private fun reference(client: String? = "client") = IncomingCallReference(CONVERSATION, SENDER, client, CALL_TIME.epochSeconds)

    private suspend fun TestScope.withJournal(block: suspend (CallSignalJournal, TestUserDatabase) -> Unit) {
        withTestUserDatabase(UserIDEntity("call-identity", "example.com")) { block(CallSignalJournal(it.builder), it) }
    }

    private companion object {
        val CALL_TIME = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() / 1000 * 1000)
        val CONVERSATION = QualifiedIDEntity("conversation", "example.com")
        val SENDER = QualifiedIDEntity("sender", "example.com")
    }
}
