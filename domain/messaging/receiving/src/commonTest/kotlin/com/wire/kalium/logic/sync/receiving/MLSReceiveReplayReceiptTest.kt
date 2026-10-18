/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.event.NewEventEntity
import com.wire.kalium.persistence.withTestUserDatabase
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8

class MLSReceiveReplayReceiptTest {
    @Test
    fun ordinaryPayloadsUseOneIndexReadAndNeverReadOrWriteNativeCheckpoint() = runTest {
        withFixture { f ->
            val invalidBase64 = EVENT.copy(content = "not base64!")
            repeat(2) {
                assertFalse(f.store.wasProcessed(f.native, invalidBase64))
                f.store.recordProcessed(f.native, listOf(invalidBase64))
            }
            assertEquals(1, f.metadata.rangeReads)
            assertEquals(0, f.metadata.writes)
            assertEquals(0, f.native.reads)
            assertEquals(0, f.native.checkpointWrites)
        }
    }

    @Test
    fun unrelatedPendingRecoveryDoesNotAddWorkToOrdinaryPayloads() = runTest {
        withFixture { f ->
            f.store.begin("unrelated", GROUP_ID, byteArrayOf(1))
            val invalidBase64 = EVENT.copy(content = "not base64!")
            assertFalse(f.store.wasProcessed(f.native, invalidBase64))
            f.store.recordProcessed(f.native, listOf(invalidBase64))
            assertEquals(1, f.metadata.rangeReads)
            assertEquals(1, f.metadata.writes)
            assertEquals(0, f.native.reads)
            assertEquals(0, f.native.checkpointWrites)
        }
    }

    @Test
    fun receiptWithoutCommittedNativeTokenDoesNotSkipAfterRollback() = runTest {
        withFixture { f ->
            f.complete()
            val committed = f.native.checkpoint
            f.store.recordProcessed(f.native, listOf(EARLIER, EVENT, LATER))
            f.native.checkpoint = committed // The enclosing native transaction rolled back; SQL candidates survived.
            f.reopen()
            for (event in listOf(EARLIER, EVENT, LATER)) assertFalse(f.store.wasProcessed(f.native, event))
            assertEquals(4, f.records().size)
        }
    }

    @Test
    fun committedReceiptsRequireExactEventAndEveryPayloadFieldButNoCurrentGroup() = runTest {
        withFixture { f ->
            f.complete()
            f.store.recordProcessed(f.native, listOf(EARLIER, EVENT, LATER))
            f.reopen()
            f.native.exists = false
            f.native.epoch = 0uL
            for (event in listOf(EARLIER, EVENT, LATER)) assertTrue(f.store.wasProcessed(f.native, event))
            val variants = listOf(
                EVENT.copy(id = "other"),
                EVENT.copy(conversationId = EVENT.conversationId.copy(value = "other")),
                EVENT.copy(conversationId = EVENT.conversationId.copy(domain = "other.example")),
                EVENT.copy(subconversationId = SubconversationId("conference")),
                EVENT.copy(senderUserId = EVENT.senderUserId.copy(value = "other")),
                EVENT.copy(senderUserId = EVENT.senderUserId.copy(domain = "other.example")),
                EVENT.copy(messageInstant = Instant.parse("2026-09-02T00:00:00Z")),
                EVENT.copy(content = "AQ"), // Same decoded byte as AQ==, different exact payload.
            )
            val reads = f.native.reads
            for (event in variants) assertFalse(f.store.wasProcessed(f.native, event))
            assertEquals(reads, f.native.reads)
            assertTrue(f.records().values.none { "conversation" in it || "AQ==" in it || "self" in it })
        }
    }

    @Test
    fun recordingRequiresEveryMatchingRecoveryCompletion() = runTest {
        withFixture { f ->
            f.complete()
            f.store.begin(LATER.id, GROUP_ID, Base64.decode(LATER.content))
            val writes = f.native.checkpointWrites
            assertFailsWith<IllegalStateException> { f.store.recordProcessed(f.native, listOf(EVENT, LATER)) }
            assertEquals(2, f.records().size)
            assertEquals(writes, f.native.checkpointWrites)
            assertFalse(f.store.wasProcessed(f.native, EVENT))
        }
    }

    @Test
    fun acknowledgingRecoveryTriggerFirstRetainsOtherReceiptsAcrossTakeoverAndPruning() = runTest {
        withFixture { f ->
            f.enqueue(EARLIER, EVENT, LATER)
            f.complete()
            f.store.recordProcessed(f.native, listOf(EARLIER, EVENT, LATER))
            f.store.acknowledge(listOf(EVENT.id))
            assertEquals(2, f.records().size)
            assertTrue(f.database.builder.eventDAO.getEventById(EVENT.id)!!.isProcessed)
            assertFalse(f.database.builder.eventDAO.getEventById(EARLIER.id)!!.isProcessed)
            f.reopen()
            f.store.recordProcessed(f.native, listOf(EARLIER)) // A narrower retry must retain LATER.
            assertTrue(f.store.wasProcessed(f.native, EARLIER))
            assertTrue(f.store.wasProcessed(f.native, LATER))
            // Completing another operation also must not prune receipts belonging to older batches.
            val another = EVENT.copy(id = "another")
            f.enqueue(another)
            f.complete(another)
            f.store.recordProcessed(f.native, listOf(another))
            f.store.acknowledge(listOf(another.id, EARLIER.id))
            f.reopen()
            assertFalse(f.store.wasProcessed(f.native, EARLIER))
            assertTrue(f.store.wasProcessed(f.native, LATER))
            assertEquals(1, f.records().size)
            f.store.acknowledge(listOf(LATER.id))
            assertTrue(f.records().isEmpty())
        }
    }

    @Test
    fun missingRecoveryEventPreventsReceiptRetirementAndInvalidatesIndex() = runTest {
        withFixture { f ->
            f.enqueue(EARLIER)
            f.complete()
            f.store.recordProcessed(f.native, listOf(EARLIER, EVENT))
            assertFailsWith<Exception> { f.store.acknowledge(listOf(EARLIER.id, EVENT.id)) }
            assertFalse(f.database.builder.eventDAO.getEventById(EARLIER.id)!!.isProcessed)
            assertEquals(3, f.records().size)
            val reads = f.metadata.rangeReads
            assertTrue(f.store.wasProcessed(f.native, EVENT))
            assertEquals(reads + 1, f.metadata.rangeReads)
            f.enqueue(EVENT)
            f.store.acknowledge(listOf(EARLIER.id, EVENT.id))
            assertTrue(f.records().isEmpty())
        }
    }

    @Test
    fun partialCandidateWriteFailureInvalidatesIndexAndNeverWritesNativeReceiptToken() = runTest {
        withFixture { f ->
            f.complete()
            f.metadata.failWrite = f.metadata.writes + 2
            val writes = f.native.checkpointWrites
            assertFailsWith<IllegalStateException> { f.store.recordProcessed(f.native, listOf(EARLIER, EVENT, LATER)) }
            assertEquals(writes, f.native.checkpointWrites)
            assertEquals(2, f.records().size)
            val reads = f.metadata.rangeReads
            assertFalse(f.store.wasProcessed(f.native, EARLIER))
            assertEquals(reads + 1, f.metadata.rangeReads)
            f.metadata.failWrite = null
            f.store.recordProcessed(f.native, listOf(EARLIER, EVENT, LATER))
            for (event in listOf(EARLIER, EVENT, LATER)) assertTrue(f.store.wasProcessed(f.native, event))
        }
    }

    @Test
    fun malformedReceiptKeysAndUnknownEntriesFailClosed() = runTest {
        val invalid = listOf(
            "payload:bad" to "{}",
            "payload:${"0".repeat(64)}" to "{\"eventId\":\"event\",\"payloadHash\":\"${"0".repeat(64)}\"}",
            "unknown:entry" to "{}",
        )
        for ((suffix, value) in invalid) withFixture { f ->
            f.metadata.insertValue(value, PREFIX + suffix)
            assertFailsWith<Exception> { f.store.wasProcessed(f.native, EVENT) }
            assertEquals(0, f.native.reads)
        }
    }

    @Test
    fun malformedNativeProcessedIdentityFailsClosed() = runTest {
        withFixture { f ->
            f.complete()
            f.store.recordProcessed(f.native, listOf(EVENT))
            f.native.checkpoint = """{"format":"kalium.mls.receive-recovery.v1","clientHash":"$CLIENT_HASH","completed":{},"processed":["bad"]}"""
                .encodeToByteArray()
            assertFailsWith<IllegalStateException> { f.store.wasProcessed(f.native, EVENT) }
        }
    }

    @Test
    fun effectReceiptsRequireExactPayloadAndNativeCommitAcrossReopen() = runTest {
        withFixture { f ->
            val payload = ReceivedPayloadIdentity(EVENT.id, EventContentDTO.Unknown("first"))
            assertFalse(f.store.wereEffectsProcessed(f.native, payload))
            assertEquals(0, f.native.reads)
            f.complete()
            val beforeReceipt = f.native.checkpoint
            f.store.recordProcessed(f.native, listOf(EVENT), listOf(payload))
            val committed = f.native.checkpoint
            f.native.checkpoint = beforeReceipt
            f.reopen()
            assertFalse(f.store.wereEffectsProcessed(f.native, payload))
            f.native.checkpoint = committed
            assertTrue(f.store.wereEffectsProcessed(f.native, payload))
            assertFalse(f.store.wereEffectsProcessed(f.native, payload.copy(content = EventContentDTO.Unknown("second"))))
            assertFalse(f.store.wereEffectsProcessed(f.native, payload.copy(eventId = "other")))
            f.enqueue(EVENT)
            f.store.acknowledge(listOf(EVENT.id))
            f.reopen()
            assertFalse(f.store.wereEffectsProcessed(f.native, payload))
        }
    }

    @Test
    fun ordinaryEffectReceiptChecksShareTheCachedIndexWithoutNativeReads() = runTest {
        withFixture { f ->
            val payload = ReceivedPayloadIdentity(EVENT.id, EventContentDTO.Unknown())
            repeat(2) {
                assertFalse(f.store.wereEffectsProcessed(f.native, payload))
                f.store.recordProcessed(f.native, listOf(EVENT), listOf(payload))
            }
            assertEquals(1, f.metadata.rangeReads)
            assertEquals(0, f.native.reads)
            assertEquals(0, f.metadata.writes)
        }
    }

    private suspend fun TestScope.withFixture(block: suspend (Fixture) -> Unit) {
        withTestUserDatabase(UserIDEntity("receipt-test", "example.com")) { block(Fixture(it)) }
    }

    private class Fixture(val database: TestUserDatabase) {
        val metadata = CountingMetadata(database.builder.metadataDAO)
        val native = FakeMlsContext()
        var store = MLSReceiveRecoveryStore(metadata, database.builder.notificationEventStore, "client")
        fun reopen() { store = MLSReceiveRecoveryStore(metadata, database.builder.notificationEventStore, "client") }
        suspend fun complete(event: Event.Conversation.NewMLSMessage = EVENT) {
            val operation = store.begin(event.id, GROUP_ID, Base64.decode(event.content))
            store.complete(native, operation)
        }
        suspend fun records() = database.builder.metadataDAO.valuesByKeyRange(PREFIX, "mls_receive_recovery_v2:$CLIENT_HASH;")
        suspend fun enqueue(vararg events: Event.Conversation.NewMLSMessage) {
            val eventStore = database.builder.notificationEventStore
            eventStore.storePage(eventStore.cursor(), events.map { NewEventEntity(it.id, "[]", false, false) })
        }
    }

    private class CountingMetadata(private val delegate: MetadataDAO) : MetadataDAO by delegate {
        var rangeReads = 0
        var writes = 0
        var failWrite: Int? = null
        override suspend fun valuesByKeyRange(fromInclusive: String, untilExclusive: String): Map<String, String> {
            rangeReads++
            return delegate.valuesByKeyRange(fromInclusive, untilExclusive)
        }
        override suspend fun insertValue(value: String, key: String) {
            writes++
            check(writes != failWrite) { "Receipt write failed" }
            delegate.insertValue(value, key)
        }
    }

    private companion object {
        const val GROUP_ID = "group"
        val CLIENT_HASH = "client".encodeUtf8().sha256().hex()
        val PREFIX = "mls_receive_recovery_v2:$CLIENT_HASH:"
        val EVENT = Event.Conversation.NewMLSMessage(
            "event", ConversationId("conversation", "example.com"), null, UserId("self", "example.com"),
            Instant.parse("2026-09-01T00:00:00Z"), "AQ==",
        )
        val EARLIER = EVENT.copy(id = "earlier", content = "Ag==")
        val LATER = EVENT.copy(id = "later", content = "Aw==")
    }
}
