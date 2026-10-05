/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
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
        val database = TestUserDatabase(UserIDEntity("call-identity", "example.com"), StandardTestDispatcher(testScheduler))
        try {
            block(CallSignalJournal(database.builder), database)
        } finally {
            database.builder.close()
            database.delete()
        }
    }

    private companion object {
        val CALL_TIME = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() / 1000 * 1000)
        val CONVERSATION = QualifiedIDEntity("conversation", "example.com")
        val SENDER = QualifiedIDEntity("sender", "example.com")
    }
}
