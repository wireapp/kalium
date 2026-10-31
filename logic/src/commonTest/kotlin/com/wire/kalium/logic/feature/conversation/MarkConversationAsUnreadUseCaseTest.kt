/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.answering.returns
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MarkConversationAsUnreadUseCaseTest {

    @Test
    fun givenVisibleMessage_whenMarkingUnread_thenPersistsBoundaryAndSyncsBothSelfConversations() = runTest {
        val arrangement = Arrangement(backgroundScope).apply { stub() }

        assertEquals(MarkConversationAsUnreadResult.Success, arrangement.subject()(CONVERSATION.id, MESSAGE.id))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversations.markConversationAsUnread(CONVERSATION.id, MESSAGE.date, NOW)
        }
        arrangement.selfIds.forEach { selfId ->
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.sender.sendMessage(matches { message ->
                    message.conversationId == selfId && message.senderUserId == TestUser.SELF.id && message.isSelfMessage &&
                        message.content == MessageContent.MarkUnread(CONVERSATION.id, MESSAGE.date, NOW)
                }, any())
            }
        }
        verifySuspend(VerifyMode.not) { arrangement.conversations.updateConversationReadDate(any(), any()) }
    }

    @Test
    fun givenPendingOrHiddenMessage_whenMarkingUnread_thenDoesNotPersistOrSync() = runTest {
        listOf(MESSAGE.copy(status = Message.Status.Pending), MESSAGE.copy(visibility = Message.Visibility.HIDDEN)).forEach { message ->
            val arrangement = Arrangement(backgroundScope).apply { stub(message = message) }

            assertIs<MarkConversationAsUnreadResult.Failure>(arrangement.subject()(CONVERSATION.id, message.id))

            verifySuspend(VerifyMode.not) { arrangement.conversations.markConversationAsUnread(any(), any(), any()) }
            verifySuspend(VerifyMode.not) { arrangement.sender.sendMessage(any(), any()) }
        }
    }

    @Test
    fun givenStoredOperationAheadOfClock_whenMarkingUnread_thenAdvancesItsVersionForLocalAndRemoteState() = runTest {
        val previousOperation = NOW + 1.seconds
        val arrangement = Arrangement(backgroundScope).apply { stub(conversation = CONVERSATION.copy(manualUnreadUpdatedAt = previousOperation)) }

        assertEquals(MarkConversationAsUnreadResult.Success, arrangement.subject()(CONVERSATION.id, MESSAGE.id))

        val expectedOperation = previousOperation + 1.milliseconds
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversations.markConversationAsUnread(CONVERSATION.id, MESSAGE.date, expectedOperation)
        }
        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.sender.sendMessage(matches {
                (it.content as? MessageContent.MarkUnread)?.operationTime == expectedOperation
            }, any())
        }
    }

    @Test
    fun givenOneSelfTransportFails_whenMarkingUnread_thenKeepsLocalMarkerAndStillTriesOtherTransport() = runTest {
        val arrangement = Arrangement(backgroundScope).apply { stub() }
        everySuspend {
            arrangement.sender.sendMessage(matches { it.conversationId == arrangement.selfIds.first() }, any())
        } returns Either.Left(StorageFailure.DataNotFound)

        assertEquals(
            MarkConversationAsUnreadResult.Failure(StorageFailure.DataNotFound),
            arrangement.subject()(CONVERSATION.id, MESSAGE.id)
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversations.markConversationAsUnread(CONVERSATION.id, MESSAGE.date, NOW)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sender.sendMessage(matches { it.conversationId == arrangement.selfIds.last() }, any())
        }
    }

    @Test
    fun leavingTheConversationDoesNotCancelAnAcceptedUnreadSync() = runTest {
        val arrangement = Arrangement(backgroundScope).apply { stub() }
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        everySuspend { arrangement.sender.sendMessage(any(), any()) } calls {
            started.complete(Unit)
            finish.await()
            Either.Right(Unit)
        }
        val caller = launch { arrangement.subject()(CONVERSATION.id, MESSAGE.id) }
        started.await()

        caller.cancelAndJoin()
        finish.complete(Unit)
        runCurrent()

        verifySuspend(VerifyMode.exactly(2)) { arrangement.sender.sendMessage(any(), any()) }
    }

    private class Arrangement(private val scope: CoroutineScope) {
        val conversations = mock<ConversationRepository>()
        private val messages = mock<MessageRepository>()
        val sender = mock<MessageSender>()
        val selfIds = listOf(ConversationId("self-proteus", "wire.com"), ConversationId("self-mls", "wire.com"))

        suspend fun stub(message: Message.Regular = MESSAGE, conversation: Conversation = CONVERSATION) {
            everySuspend { messages.getMessageById(any(), any()) } returns Either.Right(message)
            everySuspend { conversations.getConversationById(any()) } returns Either.Right(conversation)
            everySuspend { conversations.markConversationAsUnread(any(), any(), any()) } returns Either.Right(Unit)
            everySuspend { sender.sendMessage(any(), any()) } returns Either.Right(Unit)
        }

        fun subject() = MarkConversationAsUnreadUseCaseImpl(
            conversationRepository = conversations,
            messageRepository = messages,
            messageSender = sender,
            currentClientIdProvider = { Either.Right(TestClient.CLIENT_ID) },
            selfUserId = TestUser.SELF.id,
            selfConversationIdProvider = { Either.Right(selfIds) },
            scope = scope,
            clock = object : Clock {
                override fun now() = NOW
            },
        )
    }

    private companion object {
        val NOW = Instant.parse("2026-02-10T12:00:00Z")
        val CONVERSATION = TestConversation.CONVERSATION.copy(lastReadDate = NOW)
        val MESSAGE = TestMessage.TEXT_MESSAGE.copy(
            conversationId = CONVERSATION.id,
            date = NOW - 10.seconds,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
        )
    }
}
