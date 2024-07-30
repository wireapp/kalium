/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
<<<<<<< HEAD
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.instanceOf
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
=======
import io.mockative.anyInstanceOf
import io.mockative.anything
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
>>>>>>> 28e19a6056 (fix: send calling message in order [WPB-10051] (#2920))
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class CallingMessageSenderTest {

    @Test
    fun givenSendInSelfConversation_whenSending_messageIsSentInSelfConversations() = runTest {
        val (arrangement, sender) = Arrangement(this)
            .givenSelfConversationIdProviderReturns(Either.Right(listOf(Arrangement.selfConversationId)))
            .givenSendMessageSuccessful()
            .arrange()

        val processingJob = launch {
            sender.processQueue()
        }

        sender.enqueueSendingOfCallingMessage(
            context = null,
            messageString = "message",
            callHostConversationId = Arrangement.conversationId,
            avsSelfUserId = Arrangement.selfUserId,
            avsSelfClientId = Arrangement.selfUserCLientId,
            messageTarget = CallingMessageTarget.Self
        )
        advanceUntilIdle()

<<<<<<< HEAD
        coVerify {
            arrangement.messageSender.sendMessage(
                matches { it.conversationId == Arrangement.selfConversationId },
                matches { it is MessageTarget.Conversation },
            )
        }.wasInvoked(exactly = once)
=======
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(
                matching { it.conversationId == Arrangement.selfConversationId },
                matching { it is MessageTarget.Conversation }
            )
            .wasInvoked(exactly = once)
>>>>>>> 28e19a6056 (fix: send calling message in order [WPB-10051] (#2920))
        processingJob.cancel()
    }

    @Test
    fun givenSendFails_whenSending_thenAvsIsInformedAboutItWithCode400() = runTest {
        val (arrangement, sender) = Arrangement(this)
            .givenSelfConversationIdProviderReturns(Either.Right(listOf(Arrangement.selfConversationId)))
            .givenSendMessageFails()
            .arrange()

        val processingJob = launch {
            sender.processQueue()
        }

        val contextPointer = Pointer.createConstant(24)

        sender.enqueueSendingOfCallingMessage(
            context = contextPointer,
            messageString = "message",
            callHostConversationId = Arrangement.conversationId,
            avsSelfUserId = Arrangement.selfUserId,
            avsSelfClientId = Arrangement.selfUserCLientId,
            messageTarget = CallingMessageTarget.Self
        )
        advanceUntilIdle()

<<<<<<< HEAD
        coVerify {
            arrangement.calling.wcall_resp(
=======
        verify(arrangement.calling)
            .function(arrangement.calling::wcall_resp)
            .with(
>>>>>>> 28e19a6056 (fix: send calling message in order [WPB-10051] (#2920))
                eq(arrangement.handle),
                eq(400),
                any(),
                eq(contextPointer),
<<<<<<< HEAD
            )
        }.wasInvoked(exactly = once)
=======
            ).wasInvoked(exactly = once)
>>>>>>> 28e19a6056 (fix: send calling message in order [WPB-10051] (#2920))
        processingJob.cancel()
    }

    @Test
    fun givenSendSucceeds_whenSending_thenAvsIsInformedAboutItWithCode200() = runTest {
        val (arrangement, sender) = Arrangement(this)
            .givenSelfConversationIdProviderReturns(Either.Right(listOf(Arrangement.selfConversationId)))
            .givenSendMessageSuccessful()
            .arrange()

        val processingJob = launch {
            sender.processQueue()
        }

        val contextPointer = Pointer.createConstant(123)

        sender.enqueueSendingOfCallingMessage(
            context = contextPointer,
            messageString = "message",
            callHostConversationId = Arrangement.conversationId,
            avsSelfUserId = Arrangement.selfUserId,
            avsSelfClientId = Arrangement.selfUserCLientId,
            messageTarget = CallingMessageTarget.Self
        )
        advanceUntilIdle()

<<<<<<< HEAD
        coVerify {
            arrangement.calling.wcall_resp(
=======
        verify(arrangement.calling)
            .function(arrangement.calling::wcall_resp)
            .with(
>>>>>>> 28e19a6056 (fix: send calling message in order [WPB-10051] (#2920))
                eq(arrangement.handle),
                eq(200),
                any(),
                eq(contextPointer),
<<<<<<< HEAD
            )
        }.wasInvoked(exactly = once)
=======
            ).wasInvoked(exactly = once)
>>>>>>> 28e19a6056 (fix: send calling message in order [WPB-10051] (#2920))
        processingJob.cancel()
    }

    @Test
    fun givenSendInHostConversation_whenSending_messageIsSentInTargetConversation() = runTest {
        val (arrangement, sender) = Arrangement(this)
            .givenSendMessageSuccessful()
            .arrange()

        val processingJob = launch {
            sender.processQueue()
        }

        sender.enqueueSendingOfCallingMessage(
            context = null,
            messageString = "message",
            callHostConversationId = Arrangement.conversationId,
            avsSelfUserId = Arrangement.selfUserId,
            avsSelfClientId = Arrangement.selfUserCLientId,
            messageTarget = CallingMessageTarget.HostConversation()
        )
        advanceUntilIdle()

<<<<<<< HEAD
        coVerify {
            arrangement.messageSender.sendMessage(
                matches { it.conversationId == Arrangement.conversationId },
                matches { it is MessageTarget.Conversation },
            )
        }.wasInvoked(exactly = once)
=======
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(
                matching { it.conversationId == Arrangement.conversationId },
                matching { it is MessageTarget.Conversation },
            ).wasInvoked(exactly = once)
>>>>>>> 28e19a6056 (fix: send calling message in order [WPB-10051] (#2920))
        processingJob.cancel()
    }

    @Test
    fun givenMultipleMessagesAreEnqueued_whenSending_messagesAreSentInOrder() = runTest {
        var invokeCount = 0
        val firstMessageLock = Job()

        val (arrangement, sender) = Arrangement(this)
            .givenSendMessageInvokes {
                invokeCount++
                if (invokeCount == 1) { // Delay the FIRST sending
                    firstMessageLock.join()
                }
                Either.Right(Unit)
            }
            .arrange()

        val processingJob = launch {
            sender.processQueue()
        }

        val firstMessageText = "message"
        sender.enqueueSendingOfCallingMessage(
            context = null,
            messageString = firstMessageText,
            callHostConversationId = Arrangement.conversationId,
            avsSelfUserId = Arrangement.selfUserId,
            avsSelfClientId = Arrangement.selfUserCLientId,
            messageTarget = CallingMessageTarget.HostConversation()
        )
        val secondMessageText = "SECOND message"
        sender.enqueueSendingOfCallingMessage(
            context = null,
            messageString = secondMessageText,
            callHostConversationId = Arrangement.conversationId,
            avsSelfUserId = Arrangement.selfUserId,
            avsSelfClientId = Arrangement.selfUserCLientId,
            messageTarget = CallingMessageTarget.HostConversation()
        )

        advanceUntilIdle()

        assertEquals(1, invokeCount)
<<<<<<< HEAD
        coVerify {
            arrangement.messageSender.sendMessage(
                matches {
=======
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(
                matching {
>>>>>>> 28e19a6056 (fix: send calling message in order [WPB-10051] (#2920))
                    val content = it.content
                    secondMessageText == (content as? MessageContent.Calling)?.value &&
                            it.conversationId == Arrangement.conversationId
                },
<<<<<<< HEAD
                instanceOf<MessageTarget.Conversation>()
            )
        }.wasNotInvoked()

        firstMessageLock.cancel()
        advanceUntilIdle()

        coVerify {
            arrangement.messageSender.sendMessage(
                matches {
=======
                anyInstanceOf(MessageTarget.Conversation::class)
            )
            .wasNotInvoked()

        firstMessageLock.cancel()
        advanceUntilIdle()
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(
                matching {
>>>>>>> 28e19a6056 (fix: send calling message in order [WPB-10051] (#2920))
                    val content = it.content
                    secondMessageText == (content as? MessageContent.Calling)?.value &&
                            it.conversationId == Arrangement.conversationId
                },
<<<<<<< HEAD
                instanceOf<MessageTarget.Conversation>()
            )
        }.wasInvoked(exactly = once)
=======
                anyInstanceOf(MessageTarget.Conversation::class)
            )
            .wasInvoked(exactly = once)
>>>>>>> 28e19a6056 (fix: send calling message in order [WPB-10051] (#2920))
        processingJob.cancel()
    }

    internal class Arrangement(private val testScope: CoroutineScope) {

        @Mock
        val calling = mock(Calling::class)

        @Mock
        var messageSender = mock(MessageSender::class)

        @Mock
        val selfConversationIdProvider = mock(SelfConversationIdProvider::class)

        val handle = Handle(42)

        init {
<<<<<<< HEAD
            every { calling.wcall_resp(any(), any(), any(), any()) }.returns(0)
=======
            given(calling)
                .function(calling::wcall_resp)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(0)
>>>>>>> 28e19a6056 (fix: send calling message in order [WPB-10051] (#2920))
        }

        fun arrange() = this to CallingMessageSender(
            testScope.async { handle },
            calling,
            messageSender,
            testScope,
            selfConversationIdProvider
        )

        companion object {
            val conversationId = TestConversation.GROUP().id
            val selfConversationId = TestConversation.SELF().id
            val selfUserId = TestUser.SELF.id
            val selfUserCLientId = ClientId("self_client")
        }

        suspend fun givenSelfConversationIdProviderReturns(result: Either<StorageFailure, List<ConversationId>>) = apply {
<<<<<<< HEAD
            coEvery {
                selfConversationIdProvider.invoke()
            }.returns(result)
        }

        suspend fun givenSendMessageSuccessful() = apply {
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun givenSendMessageFails() = apply {
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun givenSendMessageInvokes(block: suspend () -> Either<CoreFailure, Unit>) = apply {
            coEvery {
                messageSender.sendMessage(any(), any())
            }.invokes(block)
=======
            given(selfConversationIdProvider)
                .suspendFunction(selfConversationIdProvider::invoke)
                .whenInvoked()
                .thenReturn(result)
        }

        suspend fun givenSendMessageSuccessful() = apply {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        suspend fun givenSendMessageFails() = apply {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun givenSendMessageInvokes(block: suspend () -> Either<CoreFailure, Unit>) = apply {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(any(), any())
                .thenInvoke(block)
>>>>>>> 28e19a6056 (fix: send calling message in order [WPB-10051] (#2920))
        }
    }
}
