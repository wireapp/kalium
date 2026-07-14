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
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdatedHandler
import com.wire.kalium.logic.sync.receiver.handler.TypingIndicatorHandler
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newMessageEventHandler.handleNewProteusMessage(any(), eq(newMessageEvent), any())
        }

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newMessageEventHandler.handleNewMLSMessage(any(), eq(newMLSMessageEvent), any())
        }

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newConversationEventHandler.handle(any(), eq(newConversationEvent))
        }

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.deletedConversationEventHandler.handle(any(), eq(deletedConversationEvent))
        }

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberJoinEventHandler.handle(any(), eq(memberJoinEvent))
        }

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberLeaveEventHandler.handle(any(), eq(memberLeaveEvent))
        }

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberChangeEventHandler.handle(any(), eq(memberChangeEvent))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsWelcomeEventHandler.handle(any(), eq(mlsWelcomeEvent))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.renamedConversationEventHandler.handle(eq(renamedConversationEvent))
        }
        result.shouldSucceed()
    }

    @Test
    fun givenRenamedConversationEvent_whenHandlerFails_thenFailureIsPropagated() = runTest {
        val renamedConversationEvent = TestEvent.renamedConversation()
        val (arrangement, conversationEventReceiver) = Arrangement().arrange {
            withRenamedConversationEventResult(Either.Left(failure))
        }

        val result = conversationEventReceiver.onEvent(
            arrangement.transactionContext,
            renamedConversationEvent,
            TestEvent.liveDeliveryInfo
        )

        result.shouldFail()
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.receiptModeUpdateEventHandler.handle(eq(receiptModeUpdateEvent))
            }
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationMessageTimerEventHandler.handle(eq(conversationMessageTimerEvent))
            }

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.codeUpdatedHandler.handle(eq(codeUpdatedEvent))
        }

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.codeUpdatedHandler.handle(eq(codeUpdatedEvent))
        }

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.codeDeletedHandler.handle(eq(codeUpdatedEvent))
        }

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.codeDeletedHandler.handle(eq(codeUpdatedEvent))
        }

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.typingIndicatorHandler.handle(eq(typingStarted))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.typingIndicatorHandler.handle(eq(typingStarted))
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.accessUpdateEventHandler.handle(eq(accessUpdateEvent))
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.accessUpdateEventHandler.handle(eq(accessUpdateEvent))
        }
    }

    private class Arrangement :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {

        val conversationMessageTimerEventHandler = mock<ConversationMessageTimerEventHandler>()
        val receiptModeUpdateEventHandler = mock<ReceiptModeUpdateEventHandler>()
        val renamedConversationEventHandler = mock<RenamedConversationEventHandler>()
        val mlsWelcomeEventHandler = mock<MLSWelcomeEventHandler>()
        val memberChangeEventHandler = mock<MemberChangeEventHandler>()
        val memberLeaveEventHandler = mock<MemberLeaveEventHandler>()
        val memberJoinEventHandler = mock<MemberJoinEventHandler>()
        val newMessageEventHandler = mock<NewMessageEventHandler>()
        val newConversationEventHandler = mock<NewConversationEventHandler>()
        val deletedConversationEventHandler = mock<DeletedConversationEventHandler>()
        val typingIndicatorHandler = mock<TypingIndicatorHandler>()
        val protocolUpdateEventHandler = mock<ProtocolUpdateEventHandler>()
        val channelAddPermissionUpdateEventHandler = mock<ChannelAddPermissionUpdateEventHandler>()
        val accessUpdateEventHandler = mock<AccessUpdateEventHandler>()
        val mlsResetConversationEventHandler = mock<MLSResetConversationEventHandler>()
        val codeUpdatedHandler = mock<CodeUpdatedHandler>()
        val codeDeletedHandler = mock<CodeDeletedHandler>()

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
            runBlocking {
                withDefaultSuccessfulHandlers()
                block()
            }
            this to conversationEventReceiver
        }

        private suspend fun withDefaultSuccessfulHandlers() {
            everySuspend { newMessageEventHandler.handleNewProteusMessage(any(), any(), any()) } returns Unit
            everySuspend { newMessageEventHandler.handleNewMLSMessage(any(), any(), any()) } returns Unit
            everySuspend { newConversationEventHandler.handle(any(), any()) } returns Unit
            everySuspend { deletedConversationEventHandler.handle(any(), any()) } returns Unit
            everySuspend { memberJoinEventHandler.handle(any(), any()) } returns Either.Right(Unit)
            everySuspend { memberLeaveEventHandler.handle(any(), any()) } returns Either.Right(Unit)
            everySuspend { memberChangeEventHandler.handle(any(), any()) } returns Unit
            everySuspend { mlsWelcomeEventHandler.handle(any(), any()) } returns Either.Right(Unit)
            everySuspend { renamedConversationEventHandler.handle(any()) } returns Either.Right(Unit)
            everySuspend { receiptModeUpdateEventHandler.handle(any()) } returns Unit
            everySuspend { protocolUpdateEventHandler.handle(any(), any()) } returns Either.Right(Unit)
            everySuspend { channelAddPermissionUpdateEventHandler.handle(any()) } returns Either.Right(Unit)
            everySuspend { mlsResetConversationEventHandler.handle(any(), any()) } returns Unit
        }

        suspend fun withMemberLeaveSucceeded() = apply {
            everySuspend {
                memberLeaveEventHandler.handle(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withRenamedConversationEventResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                renamedConversationEventHandler.handle(any())
            } returns result
        }

        suspend fun withMemberJoinSucceeded() = apply {
            everySuspend {
                memberJoinEventHandler.handle(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withConversationMessageTimerFailed() = apply {
            everySuspend {
                conversationMessageTimerEventHandler.handle(any())
            } returns Either.Left(failure)
        }

        suspend fun withConversationTypingEventSucceeded(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                typingIndicatorHandler.handle(any())
            } returns result
        }

        suspend fun withConversationAccessUpdateEventSucceeded(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                accessUpdateEventHandler.handle(any())
            } returns result
        }

        suspend fun withMLSWelcomeEventSucceeded() = apply {
            everySuspend {
                mlsWelcomeEventHandler.handle(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withHandleCodeUpdatedEvent(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                codeUpdatedHandler.handle(any())
            } returns result
        }

        suspend fun withHandleCodeDeleteEvent(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                codeDeletedHandler.handle(any())
            } returns result
        }
    }

    companion object {
        val failure = CoreFailure.MissingClientRegistration
    }
}
