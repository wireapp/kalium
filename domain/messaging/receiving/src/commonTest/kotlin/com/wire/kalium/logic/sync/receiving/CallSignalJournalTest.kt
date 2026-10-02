/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class CallSignalJournalTest {

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

    private companion object {
        val CONVERSATION = QualifiedIDEntity("conversation", "example.com")
        val SENDER = QualifiedIDEntity("sender", "example.com")
    }
}
