/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.withTestUserDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

class AppleMissedCallPersistenceTest {
    @Test
    fun extensionPersistsMissedConferenceWithoutRingingForMutedOrAwayUsers() = runTest {
        for (muted in listOf(false, true)) {
            withTestUserDatabase(SELF) { database ->
                val db = database.builder
                db.userDAO.insertOrIgnoreIncompleteUsers(listOf(SELF, SENDER))
                db.userDAO.updateUserAvailabilityStatus(SELF, if (muted) UserAvailabilityStatusEntity.NONE else UserAvailabilityStatusEntity.AWAY)
                db.conversationDAO.insertConversation(conversation().copy(
                    type = ConversationEntity.Type.GROUP,
                    mutedStatus = if (muted) ConversationEntity.MutedStatus.ALL_MUTED else ConversationEntity.MutedStatus.ALL_ALLOWED,
                ))
                val start = event("start", "GROUPSTART")
                val end = event("end", "CONFEND").copy(time = start.time)
                val journal = CallSignalJournal(db)
                val source = journal.append(start)
                journal.append(end)
                val results = evaluateCallingEvents(listOf(start, end), UserId(SELF.value, SELF.domain), "abc123", db)
                assertEquals(listOf(ReceivedCallType.MISSED), results.map { it.type })
                assertFalse(results.single().shouldNotify)
                val message = assertNotNull(db.messageDAO.getMessageById("missed-call:start", CONVERSATION))
                assertEquals(MessageEntityContent.MissedCall, message.content)
                assertTrue(journal.signals().isEmpty())
                // An app callback queued before the extension outcome uses the same message identity.
                assertTrue(CallSignalJournal(db).persistMissedCall(source.missedCallSource(), SELF))
                assertEquals(message, db.messageDAO.getMessageById("missed-call:start", CONVERSATION))
            }
        }
    }

    @Test
    fun lateOneToOneStartPersistsMissedCallWithoutCallKit() = runTest {
        withTestUserDatabase(SELF) { database ->
            val db = database.builder
            db.userDAO.insertOrIgnoreIncompleteUsers(listOf(SELF, SENDER))
            db.conversationDAO.insertConversation(conversation())
            val start = event("late-start", "SETUP").copy(
                payload = """{"version":"3.0","type":"SETUP","sessid":"late-call","resp":false,"sdp":"v=0","props":{"videosend":"false"}}""",
                time = Clock.System.now() - 90.seconds,
                isGroup = false,
                isMLS = false,
            )
            val journal = CallSignalJournal(db)
            journal.append(start)
            val results = evaluateCallingEvents(listOf(start), UserId(SELF.value, SELF.domain), "abc123", db)
            assertEquals(listOf(ReceivedCallType.MISSED), results.map { it.type })
            assertNotNull(db.messageDAO.getMessageById("missed-call:late-start", CONVERSATION))
            assertTrue(journal.signals().isEmpty())
        }
    }

    @Test
    fun delayedAnswerOrRejectionOnAnotherDeviceDoesNotBecomeMissed() = runTest {
        for (type in listOf("SETUP", "REJECT")) {
            withTestUserDatabase(SELF) { database ->
                val db = database.builder
                db.userDAO.insertOrIgnoreIncompleteUsers(listOf(SELF, SENDER))
                db.conversationDAO.insertConversation(conversation())
                val start = oneToOneStart("answered-start")
                val answer = start.copy(
                    messageId = "handled", senderId = SELF, senderClientId = "other-client", time = start.time + 5.seconds,
                    payload = """{"version":"3.0","type":"$type","sessid":"other-device-session","resp":${type == "SETUP"},"sdp":"v=0"}""",
                )
                val end = start.copy(
                    messageId = "end", time = start.time + 10.seconds,
                    payload = """{"version":"3.0","type":"CANCEL","sessid":"late-call","resp":false}""",
                )
                val journal = CallSignalJournal(db)
                val source = journal.append(start)
                journal.append(answer)
                journal.append(end)
                // The app's missed callback must also respect the already received own-device response.
                assertFalse(journal.persistMissedCall(source.missedCallSource(), SELF))
                val results = evaluateCallingEvents(listOf(start, answer, end), UserId(SELF.value, SELF.domain), "abc123", db)
                assertTrue(results.none { it.type == ReceivedCallType.MISSED })
                assertNull(db.messageDAO.getMessageById("missed-call:answered-start", CONVERSATION))
            }
        }
    }

    @Test
    fun delayedConferenceWaitsForEndBeforeRecordingMissedCall() = runTest {
        withTestUserDatabase(SELF) { database ->
            val db = database.builder
            db.userDAO.insertOrIgnoreIncompleteUsers(listOf(SELF, SENDER))
            db.conversationDAO.insertConversation(conversation().copy(type = ConversationEntity.Type.GROUP))
            val start = event("late-conference", "GROUPSTART").copy(time = Clock.System.now() - 90.seconds)
            val journal = CallSignalJournal(db)
            journal.append(start)
            val self = UserId(SELF.value, SELF.domain)
            val first = evaluateCallingEvents(listOf(start), self, "abc123", db)
            assertTrue(first.none { it.type == ReceivedCallType.MISSED })
            assertNull(db.messageDAO.getMessageById("missed-call:late-conference", CONVERSATION))
            assertEquals(1, journal.signals().size)
            val end = event("conference-end", "CONFEND")
            journal.append(end)
            val second = evaluateCallingEvents(listOf(start, end), self, "abc123", db)
            assertEquals(1, second.count { it.type == ReceivedCallType.MISSED })
            assertNotNull(db.messageDAO.getMessageById("missed-call:late-conference", CONVERSATION))
            assertTrue(journal.signals().isEmpty())
        }
    }

    @Test
    fun startAndCancellationReceivedTogetherPersistEvenWithoutNativeMissedCallback() = runTest {
        withTestUserDatabase(SELF) { database ->
            val db = database.builder
            db.userDAO.insertOrIgnoreIncompleteUsers(listOf(SELF, SENDER))
            db.conversationDAO.insertConversation(conversation())
            val start = oneToOneStart("cancelled-start").copy(time = Clock.System.now() - 10.seconds)
            val end = start.copy(
                messageId = "cancelled-end", time = start.time,
                payload = """{"version":"3.0","type":"CANCEL","sessid":"late-call","resp":false}""",
            )
            val journal = CallSignalJournal(db)
            journal.append(start)
            journal.append(end)
            val results = evaluateCallingEvents(listOf(start, end), UserId(SELF.value, SELF.domain), "abc123", db)
            assertEquals(listOf(ReceivedCallType.MISSED), results.map { it.type })
            assertNotNull(db.messageDAO.getMessageById("missed-call:cancelled-start", CONVERSATION))
            assertTrue(journal.signals().isEmpty())
        }
    }

    @Test
    fun ownConferenceAnswerOverridesTheLightweightMissedVerdict() = runTest {
        withTestUserDatabase(SELF) { database ->
            val db = database.builder
            db.userDAO.insertOrIgnoreIncompleteUsers(listOf(SELF, SENDER))
            db.conversationDAO.insertConversation(conversation().copy(type = ConversationEntity.Type.GROUP))
            val start = event("answered-conference", "GROUPSTART").copy(time = Clock.System.now() - 10.seconds)
            val answer = start.copy(
                messageId = "conference-answer", time = start.time + 1.seconds, senderId = SELF, senderClientId = "other-client",
                payload = """{"version":"3.0","type":"GROUPSTART","sessid":"call","resp":true}""",
            )
            val end = event("answered-end", "CONFEND")
            val journal = CallSignalJournal(db)
            listOf(start, answer).forEach { journal.append(it) }
            val ongoing = evaluateCallingEvents(listOf(start, answer), UserId(SELF.value, SELF.domain), "abc123", db)
            assertTrue(ongoing.none { it.type == ReceivedCallType.MISSED })
            assertEquals(2, journal.signals().size) // Keep the ongoing conference available to the full engine.
            journal.append(end)
            val results = evaluateCallingEvents(listOf(start, answer, end), UserId(SELF.value, SELF.domain), "abc123", db)
            assertTrue(results.none { it.type == ReceivedCallType.MISSED })
            assertNull(db.messageDAO.getMessageById("missed-call:answered-conference", CONVERSATION))
        }
    }

    private fun oneToOneStart(id: String) = event(id, "SETUP").copy(
        payload = """{"version":"3.0","type":"SETUP","sessid":"late-call","resp":false,"sdp":"v=0","props":{"videosend":"false"}}""",
        time = Clock.System.now() - 90.seconds,
        isGroup = false,
        isMLS = false,
    )

    private fun event(id: String, type: String) = ReceivedCallEvent(
        payload = """{"version":"3.0","type":"$type","sessid":"call","resp":false}""",
        conversationId = CONVERSATION, senderId = SENDER, senderClientId = "def456",
        time = Clock.System.now() - 1.seconds, isGroup = true, isMLS = true, messageId = id,
    )

    private fun conversation() = ConversationEntity(
        id = CONVERSATION, name = "Conversation", type = ConversationEntity.Type.ONE_ON_ONE,
        teamId = null, protocolInfo = ConversationEntity.ProtocolInfo.Proteus, creatorId = SENDER.value,
        lastNotificationDate = null, lastModifiedDate = Instant.DISTANT_PAST, lastReadDate = Instant.DISTANT_PAST,
        access = emptyList(), accessRole = emptyList(), receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = null, userMessageTimer = null, archived = false, archivedInstant = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED, channelAccess = null, channelAddPermission = null,
        wireCell = null, historySharingRetentionSeconds = 0,
    )

    private companion object {
        val SELF = QualifiedIDEntity("self", "example.com")
        val SENDER = QualifiedIDEntity("caller", "example.com")
        val CONVERSATION = QualifiedIDEntity("conversation", "example.com")
    }
}
