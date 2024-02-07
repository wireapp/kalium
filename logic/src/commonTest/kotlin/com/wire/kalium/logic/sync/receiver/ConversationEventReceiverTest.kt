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
package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MLSWelcomeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberChangeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.NewConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ReceiptModeUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandler
import com.wire.kalium.logic.sync.receiver.handler.TypingIndicatorHandler
import com.wire.kalium.logic.util.arrangement.eventHandler.CodeDeletedHandlerArrangement
import com.wire.kalium.logic.util.arrangement.eventHandler.CodeDeletedHandlerArrangementImpl
import com.wire.kalium.logic.util.arrangement.eventHandler.CodeUpdatedHandlerArrangement
import com.wire.kalium.logic.util.arrangement.eventHandler.CodeUpdatedHandlerArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class ConversationEventReceiverTest {
    @Test
    fun givenNewMessageEvent_whenOnEventInvoked_thenNewMessageEventHandlerShouldBeCalled() = runTest {
        val newMessageEvent = TestEvent.newMessageEvent("some-dummy-encrypted-content")

        val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(newMessageEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.newMessageEventHandler)
            .suspendFunction(arrangement.newMessageEventHandler::handleNewProteusMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenNewMLSMessageEvent_whenOnEventInvoked_thenNewMLSMessageEventHandlerShouldBeCalled() = runTest {
        val newMLSMessageEvent = TestEvent.newMLSMessageEvent(Instant.DISTANT_FUTURE)

        val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(newMLSMessageEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.newMessageEventHandler)
            .suspendFunction(arrangement.newMessageEventHandler::handleNewMLSMessage)
            .with(eq(newMLSMessageEvent))
            .wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenNewConversationEvent_whenOnEventInvoked_thenNewConversationHandlerShouldBeCalled() = runTest {
        val newConversationEvent = TestEvent.newConversationEvent()

        val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(newConversationEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.newConversationEventHandler)
            .suspendFunction(arrangement.newConversationEventHandler::handle)
            .with(eq(newConversationEvent))
            .wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenDeletedConversationEvent_whenOnEventInvoked_thenDeletedConversationHandlerShouldBeCalled() = runTest {
        val deletedConversationEvent = TestEvent.deletedConversation()

        val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(deletedConversationEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.deletedConversationEventHandler)
            .suspendFunction(arrangement.deletedConversationEventHandler::handle)
            .with(eq(deletedConversationEvent))
            .wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenMemberJoinEvent_whenOnEventInvoked_thenMemberJoinHandlerShouldBeCalled() = runTest {
        val memberJoinEvent = TestEvent.memberJoin()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withMemberJoinSucceeded()
            .arrange()

        val result = featureConfigEventReceiver.onEvent(memberJoinEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.memberJoinEventHandler)
            .suspendFunction(arrangement.memberJoinEventHandler::handle)
            .with(eq(memberJoinEvent))
            .wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenMemberLeaveEvent_whenOnEventInvoked_thenPropagateMemberLeaveHandlerResult() = runTest {
        val memberLeaveEvent = TestEvent.memberLeave()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withMemberLeaveSucceeded()
            .arrange()

        val result = featureConfigEventReceiver.onEvent(memberLeaveEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.memberLeaveEventHandler)
            .suspendFunction(arrangement.memberLeaveEventHandler::handle)
            .with(eq(memberLeaveEvent))
            .wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenMemberChangeEvent_whenOnEventInvoked_thenMemberChangeHandlerShouldBeCalled() = runTest {
        val memberChangeEvent =
            TestEvent.memberChange(member = Conversation.Member(TestUser.USER_ID, Conversation.Member.Role.Admin))

        val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(memberChangeEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.memberChangeEventHandler)
            .suspendFunction(arrangement.memberChangeEventHandler::handle)
            .with(eq(memberChangeEvent))
            .wasInvoked(once)
        result.shouldSucceed()
    }

    @Test
    fun givenMLSWelcomeEvent_whenOnEventInvoked_thenMlsWelcomeHandlerShouldBeCalled() = runTest {
        val mlsWelcomeEvent = TestEvent.newMLSWelcomeEvent()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withMLSWelcomeEventSucceeded()
            .arrange()

        val result = featureConfigEventReceiver.onEvent(mlsWelcomeEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.mlsWelcomeEventHandler)
            .suspendFunction(arrangement.mlsWelcomeEventHandler::handle)
            .with(eq(mlsWelcomeEvent))
            .wasInvoked(once)
        result.shouldSucceed()
    }

    @Test
    fun givenRenamedConversationEvent_whenOnEventInvoked_thenRenamedConversationHandlerShouldBeCalled() = runTest {
        val renamedConversationEvent = TestEvent.renamedConversation()

        val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(renamedConversationEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.renamedConversationEventHandler)
            .suspendFunction(arrangement.renamedConversationEventHandler::handle)
            .with(eq(renamedConversationEvent))
            .wasInvoked(once)
        result.shouldSucceed()
    }

    @Test
    fun givenConversationReceiptModeEvent_whenOnEventInvoked_thenReceiptModeUpdateEventHandlerShouldBeCalled() =
        runTest {
            val receiptModeUpdateEvent = TestEvent.receiptModeUpdate()

            val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

            val result = featureConfigEventReceiver.onEvent(receiptModeUpdateEvent, TestEvent.liveDeliveryInfo)

            verify(arrangement.receiptModeUpdateEventHandler)
                .suspendFunction(arrangement.receiptModeUpdateEventHandler::handle)
                .with(eq(receiptModeUpdateEvent))
                .wasInvoked(once)
            result.shouldSucceed()
        }

    @Test
    fun givenAccessUpdateEvent_whenOnEventInvoked_thenReturnSuccess() = runTest {
        val accessUpdateEvent = TestEvent.newAccessUpdateEvent()

        val (_, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(accessUpdateEvent, TestEvent.liveDeliveryInfo)

        result.shouldSucceed()
    }

    @Test
    fun givenConversationMessageTimerEvent_whenOnEventInvoked_thenPropagateConversationMessageTimerEventHandlerResult() =
        runTest {
            val conversationMessageTimerEvent = TestEvent.timerChanged()

            val (arrangement, featureConfigEventReceiver) = Arrangement()
                .withConversationMessageTimerFailed()
                .arrange()

            val result = featureConfigEventReceiver.onEvent(conversationMessageTimerEvent, TestEvent.liveDeliveryInfo)

            verify(arrangement.conversationMessageTimerEventHandler)
                .suspendFunction(arrangement.conversationMessageTimerEventHandler::handle)
                .with(eq(conversationMessageTimerEvent))
                .wasInvoked(once)

            result.shouldFail()
        }

    @Test
    fun givenCodeUpdateEventAndHandlingSuccess_whenOnEventInvoked_thenPropagateCodeUpdatedHandlerResult() = runTest {
        val codeUpdatedEvent = TestEvent.codeUpdated()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .arrange {
                withHandleCodeUpdatedEvent(Either.Right(Unit))
            }

        val result = featureConfigEventReceiver.onEvent(codeUpdatedEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.codeUpdatedHandler)
            .suspendFunction(arrangement.codeUpdatedHandler::handle)
            .with(eq(codeUpdatedEvent))
            .wasInvoked(once)

        result.shouldSucceed()
    }


    @Test
    fun givenCodeUpdateEventAndHandlingFail_whenOnEventInvoked_thenPropagateCodeUpdatedHandlerResult() = runTest {
        val codeUpdatedEvent = TestEvent.codeUpdated()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .arrange {
                withHandleCodeUpdatedEvent(Either.Left(StorageFailure.DataNotFound))
            }

        val result = featureConfigEventReceiver.onEvent(codeUpdatedEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.codeUpdatedHandler)
            .suspendFunction(arrangement.codeUpdatedHandler::handle)
            .with(eq(codeUpdatedEvent))
            .wasInvoked(once)

        result.shouldFail()
    }


    @Test
    fun givenCodeDeleteEventAndHandlingSuccess_whenOnEventInvoked_thenPropagateCodeUpdatedHandlerResult() = runTest {
        val codeUpdatedEvent = TestEvent.codeDeleted()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .arrange {
                withHandleCodeDeleteEvent(Either.Right(Unit))
            }

        val result = featureConfigEventReceiver.onEvent(codeUpdatedEvent, TestEvent.liveDeliveryInfo)

        verify(arrangement.codeDeletedHandler)
            .suspendFunction(arrangement.codeDeletedHandler::handle)
            .with(eq(codeUpdatedEvent))
            .wasInvoked(once)

        result.shouldSucceed()
    }


    @Test
    fun givenCodeDeleteEventAndHandlingFail_whenOnEventInvoked_thenPropagateCodeUpdatedHandlerResult() = runTest {
        val codeUpdatedEvent = TestEvent.codeDeleted()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .arrange {
                withHandleCodeDeleteEvent(Either.Left(StorageFailure.DataNotFound))
            }

        val result = featureConfigEventReceiver.onEvent(codeUpdatedEvent, TestEvent.liveDeliveryInfo)


        verify(arrangement.codeDeletedHandler)
            .suspendFunction(arrangement.codeDeletedHandler::handle)
            .with(eq(codeUpdatedEvent))
            .wasInvoked(once)

        result.shouldFail()
    }

    @Test
    fun givenTypingEventAndHandlingSucceeds_whenOnEventInvoked_thenSuccessHandlerResult() = runTest {
        val typingStarted = TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STARTED)
        val (arrangement, handler) = Arrangement()
            .withConversationTypingEventSucceeded(Either.Right(Unit))
            .arrange()

        val result = handler.onEvent(typingStarted, TestEvent.liveDeliveryInfo)

        verify(arrangement.typingIndicatorHandler)
            .suspendFunction(arrangement.typingIndicatorHandler::handle)
            .with(eq(typingStarted))
            .wasInvoked(once)
        result.shouldSucceed()
    }

    @Test
    fun givenTypingEventAndHandlingFails_whenOnEventInvoked_thenSuccessHandlerPropagateFails() = runTest {
        val typingStarted = TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STARTED)
        val (arrangement, handler) = Arrangement()
            .withConversationTypingEventSucceeded(Either.Left(StorageFailure.Generic(RuntimeException("some error"))))
            .arrange()

        val result = handler.onEvent(typingStarted, TestEvent.liveDeliveryInfo)

        verify(arrangement.typingIndicatorHandler)
            .suspendFunction(arrangement.typingIndicatorHandler::handle)
            .with(eq(typingStarted))
            .wasInvoked(once)
        result.shouldFail()
    }

    private class Arrangement :
        CodeUpdatedHandlerArrangement by CodeUpdatedHandlerArrangementImpl(),
        CodeDeletedHandlerArrangement by CodeDeletedHandlerArrangementImpl() {

        @Mock
        val conversationMessageTimerEventHandler = mock(classOf<ConversationMessageTimerEventHandler>())

        @Mock
        val receiptModeUpdateEventHandler = mock(classOf<ReceiptModeUpdateEventHandler>())

        @Mock
        val renamedConversationEventHandler = mock(classOf<RenamedConversationEventHandler>())

        @Mock
        val mlsWelcomeEventHandler = mock(classOf<MLSWelcomeEventHandler>())

        @Mock
        val memberChangeEventHandler = mock(classOf<MemberChangeEventHandler>())

        @Mock
        val memberLeaveEventHandler = mock(classOf<MemberLeaveEventHandler>())

        @Mock
        val memberJoinEventHandler = mock(classOf<MemberJoinEventHandler>())

        @Mock
        val newMessageEventHandler = mock(classOf<NewMessageEventHandler>())

        @Mock
        val newConversationEventHandler = mock(classOf<NewConversationEventHandler>())

        @Mock
        val deletedConversationEventHandler = mock(classOf<DeletedConversationEventHandler>())

        @Mock
        val typingIndicatorHandler = mock(classOf<TypingIndicatorHandler>())

        @Mock
        val protocolUpdateEventHandler = mock(classOf<ProtocolUpdateEventHandler>())

        private val conversationEventReceiver: ConversationEventReceiver = ConversationEventReceiverImpl(
            newMessageHandler = newMessageEventHandler,
            newConversationHandler = newConversationEventHandler,
            deletedConversationHandler = deletedConversationEventHandler,
            memberJoinHandler = memberJoinEventHandler,
            memberLeaveHandler = memberLeaveEventHandler,
            memberChangeHandler = memberChangeEventHandler,
            mlsWelcomeHandler = mlsWelcomeEventHandler,
            renamedConversationHandler = renamedConversationEventHandler,
            receiptModeUpdateEventHandler = receiptModeUpdateEventHandler,
            conversationMessageTimerEventHandler = conversationMessageTimerEventHandler,
            codeUpdatedHandler = codeUpdatedHandler,
            codeDeletedHandler = codeDeletedHandler,
            typingIndicatorHandler = typingIndicatorHandler,
            protocolUpdateEventHandler = protocolUpdateEventHandler
        )

        fun arrange(block: Arrangement.() -> Unit = {}) = apply(block).run {
            this to conversationEventReceiver
        }

        fun withMemberLeaveSucceeded() = apply {
            given(memberLeaveEventHandler)
                .suspendFunction(memberLeaveEventHandler::handle)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withMemberJoinSucceeded() = apply {
            given(memberJoinEventHandler)
                .suspendFunction(memberJoinEventHandler::handle)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withConversationMessageTimerFailed() = apply {
            given(conversationMessageTimerEventHandler)
                .suspendFunction(conversationMessageTimerEventHandler::handle)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(failure))
        }

        fun withConversationTypingEventSucceeded(result: Either<StorageFailure, Unit>) = apply {
            given(typingIndicatorHandler)
                .suspendFunction(typingIndicatorHandler::handle)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withMLSWelcomeEventSucceeded() = apply {
            given(mlsWelcomeEventHandler)
                .suspendFunction(mlsWelcomeEventHandler::handle)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }
    }

    companion object {
        val failure = CoreFailure.MissingClientRegistration
    }
}
