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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.receiver.conversation.AccessUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddPermissionUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MLSResetConversationEventHandler
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
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class ConversationEventReceiverTest {
    @Test
    fun givenNewMessageEvent_whenOnEventInvoked_thenNewMessageEventHandlerShouldBeCalled() = runTest {
        val newMessageEvent = TestEvent.newMessageEvent("some-dummy-encrypted-content")

        val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            newMessageEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.newMessageEventHandler.handleNewProteusMessage(any(), eq(newMessageEvent), any())
        }.wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenNewMLSMessageEvent_whenOnEventInvoked_thenNewMLSMessageEventHandlerShouldBeCalled() = runTest {
        val newMLSMessageEvent = TestEvent.newMLSMessageEvent(Instant.DISTANT_FUTURE)

        val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            newMLSMessageEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.newMessageEventHandler.handleNewMLSMessage(any(), eq(newMLSMessageEvent), any())
        }.wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenNewConversationEvent_whenOnEventInvoked_thenNewConversationHandlerShouldBeCalled() = runTest {
        val newConversationEvent = TestEvent.newConversationEvent()

        val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            newConversationEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.newConversationEventHandler.handle(any(), eq(newConversationEvent))
        }.wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenDeletedConversationEvent_whenOnEventInvoked_thenDeletedConversationHandlerShouldBeCalled() = runTest {
        val deletedConversationEvent = TestEvent.deletedConversation()

        val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            deletedConversationEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.deletedConversationEventHandler.handle(any(), eq(deletedConversationEvent))
        }.wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenMemberJoinEvent_whenOnEventInvoked_thenMemberJoinHandlerShouldBeCalled() = runTest {
        val memberJoinEvent = TestEvent.memberJoin()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withMemberJoinSucceeded()
            .arrange()

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            memberJoinEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.memberJoinEventHandler.handle(any(), eq(memberJoinEvent))
        }.wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenMemberLeaveEvent_whenOnEventInvoked_thenPropagateMemberLeaveHandlerResult() = runTest {
        val memberLeaveEvent = TestEvent.memberLeave()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withMemberLeaveSucceeded()
            .arrange()

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            memberLeaveEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.memberLeaveEventHandler.handle(any(), eq(memberLeaveEvent))
        }.wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenMemberChangeEvent_whenOnEventInvoked_thenMemberChangeHandlerShouldBeCalled() = runTest {
        val memberChangeEvent =
            TestEvent.memberChange(member = Conversation.Member(TestUser.USER_ID, Conversation.Member.Role.Admin))

        val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            memberChangeEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.memberChangeEventHandler.handle(any(), eq(memberChangeEvent))
        }.wasInvoked(once)
        result.shouldSucceed()
    }

    @Test
    fun givenMLSWelcomeEvent_whenOnEventInvoked_thenMlsWelcomeHandlerShouldBeCalled() = runTest {
        val mlsWelcomeEvent = TestEvent.newMLSWelcomeEvent()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withMLSWelcomeEventSucceeded()
            .arrange()

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            mlsWelcomeEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.mlsWelcomeEventHandler.handle(any(), eq(mlsWelcomeEvent))
        }.wasInvoked(once)
        result.shouldSucceed()
    }

    @Test
    fun givenRenamedConversationEvent_whenOnEventInvoked_thenRenamedConversationHandlerShouldBeCalled() = runTest {
        val renamedConversationEvent = TestEvent.renamedConversation()

        val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            renamedConversationEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.renamedConversationEventHandler.handle(eq(renamedConversationEvent))
        }.wasInvoked(once)
        result.shouldSucceed()
    }

    @Test
    fun givenConversationReceiptModeEvent_whenOnEventInvoked_thenReceiptModeUpdateEventHandlerShouldBeCalled() =
        runTest {
            val receiptModeUpdateEvent = TestEvent.receiptModeUpdate()

            val (arrangement, featureConfigEventReceiver) = Arrangement().arrange()

            val result = featureConfigEventReceiver.onEvent(
                arrangement.transactionContext,
                receiptModeUpdateEvent,
                TestEvent.liveDeliveryInfo
            )

            coVerify {
                arrangement.receiptModeUpdateEventHandler.handle(eq(receiptModeUpdateEvent))
            }.wasInvoked(once)
            result.shouldSucceed()
        }

    @Test
    fun givenConversationMessageTimerEvent_whenOnEventInvoked_thenPropagateConversationMessageTimerEventHandlerResult() =
        runTest {
            val conversationMessageTimerEvent = TestEvent.timerChanged()

            val (arrangement, featureConfigEventReceiver) = Arrangement()
                .withConversationMessageTimerFailed()
                .arrange()

            val result = featureConfigEventReceiver.onEvent(
                arrangement.transactionContext,
                conversationMessageTimerEvent,
                TestEvent.liveDeliveryInfo
            )

            coVerify {
                arrangement.conversationMessageTimerEventHandler.handle(eq(conversationMessageTimerEvent))
            }.wasInvoked(once)

            result.shouldFail()
        }

    @Test
    fun givenCodeUpdateEventAndHandlingSuccess_whenOnEventInvoked_thenPropagateCodeUpdatedHandlerResult() = runTest {
        val codeUpdatedEvent = TestEvent.codeUpdated()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .arrange {
                withHandleCodeUpdatedEvent(Either.Right(Unit))
            }

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            codeUpdatedEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.codeUpdatedHandler.handle(eq(codeUpdatedEvent))
        }.wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenCodeUpdateEventAndHandlingFail_whenOnEventInvoked_thenPropagateCodeUpdatedHandlerResult() = runTest {
        val codeUpdatedEvent = TestEvent.codeUpdated()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .arrange {
                withHandleCodeUpdatedEvent(Either.Left(StorageFailure.DataNotFound))
            }

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            codeUpdatedEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.codeUpdatedHandler.handle(eq(codeUpdatedEvent))
        }.wasInvoked(once)

        result.shouldFail()
    }

    @Test
    fun givenCodeDeleteEventAndHandlingSuccess_whenOnEventInvoked_thenPropagateCodeUpdatedHandlerResult() = runTest {
        val codeUpdatedEvent = TestEvent.codeDeleted()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .arrange {
                withHandleCodeDeleteEvent(Either.Right(Unit))
            }

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            codeUpdatedEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.codeDeletedHandler.handle(eq(codeUpdatedEvent))
        }.wasInvoked(once)

        result.shouldSucceed()
    }

    @Test
    fun givenCodeDeleteEventAndHandlingFail_whenOnEventInvoked_thenPropagateCodeUpdatedHandlerResult() = runTest {
        val codeUpdatedEvent = TestEvent.codeDeleted()

        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .arrange {
                withHandleCodeDeleteEvent(Either.Left(StorageFailure.DataNotFound))
            }

        val result = featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            codeUpdatedEvent,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.codeDeletedHandler.handle(eq(codeUpdatedEvent))
        }.wasInvoked(once)

        result.shouldFail()
    }

    @Test
    fun givenTypingEventAndHandlingSucceeds_whenOnEventInvoked_thenSuccessHandlerResult() = runTest {
        val typingStarted = TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STARTED)
        val (arrangement, handler) = Arrangement()
            .withConversationTypingEventSucceeded(Either.Right(Unit))
            .arrange()

        val result = handler.onEvent(
            arrangement.transactionContext,
            typingStarted,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.typingIndicatorHandler.handle(eq(typingStarted))
        }.wasInvoked(once)
        result.shouldSucceed()
    }

    @Test
    fun givenTypingEventAndHandlingFails_whenOnEventInvoked_thenSuccessHandlerPropagateFails() = runTest {
        val typingStarted = TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STARTED)
        val (arrangement, handler) = Arrangement()
            .withConversationTypingEventSucceeded(Either.Left(StorageFailure.Generic(RuntimeException("some error"))))
            .arrange()

        val result = handler.onEvent(
            arrangement.transactionContext,
            typingStarted,
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.typingIndicatorHandler.handle(eq(typingStarted))
        }.wasInvoked(once)
        result.shouldFail()
    }

    @Test
    fun givenAccessUpdateEventAndHandlingSucceeds_whenOnEventInvoked_thenSuccessHandlerResult() = runTest {
        // given
        val accessUpdateEvent = TestEvent.accessUpdate()
        val (arrangement, handler) = Arrangement()
            .withConversationAccessUpdateEventSucceeded(Either.Right(Unit))
            .arrange()

        // when
        val result = handler.onEvent(
            transactionContext = arrangement.transactionContext,
            event = accessUpdateEvent,
            deliveryInfo = TestEvent.liveDeliveryInfo
        )

        // then
        result.shouldSucceed()
        coVerify {
            arrangement.accessUpdateEventHandler.handle(eq(accessUpdateEvent))
        }.wasInvoked(once)
    }

    @Test
    fun givenAccessUpdateEventAndHandlingFails_whenOnEventInvoked_thenHandlerPropagateFails() = runTest {
        // given
        val accessUpdateEvent = TestEvent.accessUpdate()
        val (arrangement, handler) = Arrangement()
            .withConversationAccessUpdateEventSucceeded(Either.Left(StorageFailure.Generic(RuntimeException("some error"))))
            .arrange()

        // when
        val result = handler.onEvent(
            transactionContext = arrangement.transactionContext,
            event = accessUpdateEvent,
            deliveryInfo = TestEvent.liveDeliveryInfo
        )

        // then
        result.shouldFail()
        coVerify {
            arrangement.accessUpdateEventHandler.handle(eq(accessUpdateEvent))
        }.wasInvoked(once)
    }

    private class Arrangement :
        CodeUpdatedHandlerArrangement by CodeUpdatedHandlerArrangementImpl(),
        CodeDeletedHandlerArrangement by CodeDeletedHandlerArrangementImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val conversationMessageTimerEventHandler = mock(ConversationMessageTimerEventHandler::class)
        val receiptModeUpdateEventHandler = mock(ReceiptModeUpdateEventHandler::class)
        val renamedConversationEventHandler = mock(RenamedConversationEventHandler::class)
        val mlsWelcomeEventHandler = mock(MLSWelcomeEventHandler::class)
        val memberChangeEventHandler = mock(MemberChangeEventHandler::class)
        val memberLeaveEventHandler = mock(MemberLeaveEventHandler::class)
        val memberJoinEventHandler = mock(MemberJoinEventHandler::class)
        val newMessageEventHandler = mock(NewMessageEventHandler::class)
        val newConversationEventHandler = mock(NewConversationEventHandler::class)
        val deletedConversationEventHandler = mock(DeletedConversationEventHandler::class)
        val typingIndicatorHandler = mock(TypingIndicatorHandler::class)
        val protocolUpdateEventHandler = mock(ProtocolUpdateEventHandler::class)
        val channelAddPermissionUpdateEventHandler = mock(ChannelAddPermissionUpdateEventHandler::class)
        val accessUpdateEventHandler = mock(AccessUpdateEventHandler::class)
        val mlsResetConversationEventHandler = mock(MLSResetConversationEventHandler::class)

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
            protocolUpdateEventHandler = protocolUpdateEventHandler,
            channelAddPermissionUpdateEventHandler = channelAddPermissionUpdateEventHandler,
            accessUpdateEventHandler = accessUpdateEventHandler,
            mlsResetConversationEventHandler = mlsResetConversationEventHandler,
        )

        fun arrange(block: suspend Arrangement.() -> Unit = {}) = run {
            runBlocking { block() }
            this to conversationEventReceiver
        }

        suspend fun withMemberLeaveSucceeded() = apply {
            coEvery {
                memberLeaveEventHandler.handle(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withMemberJoinSucceeded() = apply {
            coEvery {
                memberJoinEventHandler.handle(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withConversationMessageTimerFailed() = apply {
            coEvery {
                conversationMessageTimerEventHandler.handle(any())
            }.returns(Either.Left(failure))
        }

        suspend fun withConversationTypingEventSucceeded(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                typingIndicatorHandler.handle(any())
            }.returns(result)
        }

        suspend fun withConversationAccessUpdateEventSucceeded(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                accessUpdateEventHandler.handle(any())
            }.returns(result)
        }

        suspend fun withMLSWelcomeEventSucceeded() = apply {
            coEvery {
                mlsWelcomeEventHandler.handle(any(), any())
            }.returns(Either.Right(Unit))
        }
    }

    companion object {
        val failure = CoreFailure.MissingClientRegistration
    }
}
