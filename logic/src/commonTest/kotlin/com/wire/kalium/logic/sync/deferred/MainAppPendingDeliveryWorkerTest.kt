/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.sync.deferred

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.feature.message.MessageOperationResult
import com.wire.kalium.logic.feature.message.confirmation.SendDeliverSignalUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.persistence.dao.message.PendingDeliveryConfirmationDAO
import com.wire.kalium.persistence.dao.message.PendingDeliveryConfirmationEntity
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MainAppPendingDeliveryWorkerTest {
    @Test
    fun sendOrAcknowledgementFailureKeepsWorkAndRetryMaySendAgain() = runTest {
        val fixture = Fixture()
        fixture.rows += fixture.item(1)
        fixture.sendFails = true
        assertIs<Either.Left<*>>(fixture.worker.drain())
        assertEquals(1, fixture.rows.size)
        fixture.sendFails = false
        fixture.acknowledgementFails = true
        assertIs<Either.Left<*>>(fixture.worker.drain())
        assertEquals(1, fixture.rows.size)
        fixture.acknowledgementFails = false
        assertEquals(Either.Right(Unit), fixture.worker.drain())
        assertTrue(fixture.rows.isEmpty())
        assertEquals(listOf(listOf("message-1"), listOf("message-1"), listOf("message-1")), fixture.attempts)
    }

    @Test
    fun failedFirstPageDoesNotStarveLaterConversationsAndCancellationKeepsUnsentWork() = runTest {
        val fixture = Fixture()
        fixture.rows += (1L..101L).map { fixture.item(it, if (it <= 100) "blocked" else "later") }
        fixture.blockedConversation = "blocked"
        assertIs<Either.Left<*>>(fixture.worker.drain())
        assertEquals(100, fixture.rows.size)
        assertEquals(listOf(listOf("message-101")), fixture.attempts)
        fixture.blockedConversation = null
        fixture.cancelSend = true
        assertFailsWith<CancellationException> { fixture.worker.drain() }
        assertEquals(100, fixture.rows.size)
    }

    @Test
    fun groupConversationsNeedNoDeliverySignalUnderAppPolicy() = runTest {
        val fixture = Fixture()
        fixture.rows += fixture.item(1)
        fixture.isGroup = true
        assertEquals(Either.Right(Unit), fixture.worker.drain())
        assertTrue(fixture.rows.isEmpty())
        assertTrue(fixture.attempts.isEmpty())
    }

    private class Fixture {
        val rows = mutableListOf<PendingDeliveryConfirmationEntity>()
        val attempts = mutableListOf<List<String>>()
        var sendFails = false
        var acknowledgementFails = false
        var blockedConversation: String? = null
        var cancelSend = false
        var isGroup = false
        private val queue = mock<PendingDeliveryConfirmationDAO>()
        private val conversations = mock<ConversationRepository>()
        private val send = mock<SendDeliverSignalUseCase>()
        private val sync = mock<SyncStateObserver>()
        val worker = MainAppPendingDeliveryWorker(queue, conversations, send, sync)

        init {
            everySuspend { queue.getPendingAfter(any(), any()) } calls { (after: Long, limit: Long) ->
                rows.filter { it.id > after }.sortedBy { it.id }.take(limit.toInt())
            }
            everySuspend { queue.acknowledge(any()) } calls { (ids: List<Long>) ->
                check(!acknowledgementFails) { "Interrupted before acknowledgement" }
                rows.removeAll { it.id in ids }
                Unit
            }
            everySuspend { conversations.getConversationById(any()) } calls { (id: com.wire.kalium.logic.data.id.ConversationId) ->
                if (id.value == blockedConversation) Either.Left(StorageFailure.DataNotFound) else Either.Right(
                    TestConversation.CONVERSATION.copy(id = id, type = if (isGroup) Conversation.Type.Group.Regular else Conversation.Type.OneOnOne)
                )
            }
            everySuspend { send(any(), any()) } calls { (_: Conversation, messages: List<String>) ->
                if (cancelSend) throw CancellationException("Account closed")
                attempts += messages
                if (sendFails) MessageOperationResult.Failure(CoreFailure.Unknown(null)) else MessageOperationResult.Success
            }
        }

        fun item(id: Long, conversation: String = "conversation") = PendingDeliveryConfirmationEntity(
            id, TestConversation.ID.copy(value = conversation).toDao(), "message-$id"
        )
    }
}
