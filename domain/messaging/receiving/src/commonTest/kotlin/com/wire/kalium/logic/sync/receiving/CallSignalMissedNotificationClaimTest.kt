/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CallSignalMissedNotificationClaimTest {

    @Test
    fun givenDurableSignal_whenClaimedConcurrentlyAndJournalIsRecreated_thenOnlyOneClaimSucceeds() = runTest {
        val database = TestUserDatabase(UserIDEntity("missed-claim", "example.com"), StandardTestDispatcher(testScheduler))
        try {
            val journal = CallSignalJournal(database.builder)
            val signal = journal.append(event())
            journal.submitted(signal.id, "submitted-generation")
            journal.incoming(
                IncomingCallReference(
                    QualifiedIDEntity(signal.conversationValue, signal.conversationDomain),
                    QualifiedIDEntity(signal.senderValue, signal.senderDomain),
                    signal.senderClientId,
                    signal.timeMillis / 1000,
                ),
                ring = true,
                video = false,
                generation = "incoming-generation",
            )

            val claims = listOf(
                async { CallSignalJournal(database.builder).claimMissedNotification(signal.id) },
                async { CallSignalJournal(database.builder).claimMissedNotification(signal.id) },
            ).awaitAll()

            assertEquals(1, claims.count { it })
            val persisted = CallSignalJournal(database.builder).signals().single()
            assertTrue(persisted.missedNotificationClaimed)
            assertEquals("submitted-generation", persisted.submittedGeneration)
            assertEquals("incoming-generation", persisted.incomingGeneration)
            assertFalse(CallSignalJournal(database.builder).claimMissedNotification(signal.id))
        } finally {
            database.delete()
        }
    }

    @Test
    fun givenJournalRecordWithoutClaimField_whenDeserialized_thenClaimDefaultsToFalse() {
        val record = Json.decodeFromString(
            CallSignalRecord.serializer(),
            """{
                "id":"legacy","messageId":"message","conversationValue":"conversation",
                "conversationDomain":"example.com","senderValue":"sender","senderDomain":"example.com",
                "senderClientId":"client","payload":"{}","timeMillis":100000,"isGroup":false,
                "isMLS":false,"storedAtMillis":100000,"ready":true
            }""".trimIndent(),
        )

        assertFalse(record.missedNotificationClaimed)
    }

    @Test
    fun givenUnknownSignal_whenClaimingMissedNotification_thenClaimFailsWithoutChangingJournal() = runTest {
        val database = TestUserDatabase(UserIDEntity("missed-unknown", "example.com"), StandardTestDispatcher(testScheduler))
        try {
            val journal = CallSignalJournal(database.builder)
            journal.append(event())

            assertFalse(journal.claimMissedNotification("unknown"))
            assertFalse(journal.signals().single().missedNotificationClaimed)
        } finally {
            database.delete()
        }
    }

    private fun event() = ReceivedCallEvent(
        payload = """{"type":"SETUP","resp":false}""",
        conversationId = QualifiedIDEntity("conversation", "example.com"),
        senderId = QualifiedIDEntity("sender", "example.com"),
        senderClientId = "client",
        time = Instant.fromEpochSeconds(100),
        isGroup = false,
        isMLS = false,
        messageId = "message",
    )
}
