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
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.event.MemberLeaveReason
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.incremental.EventSource
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
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedID
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@Suppress("LargeClass", "TooManyFunctions")
class ConversationEventReceiverTest {

    @Test
    fun givenNewMessageEvent_whenOnEventInvoked_thenNewMessageEventHandlerShouldBeCalled() = runTest {
        val event = Events.newMessage()
        val arrangement = Arrangement()

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("newProteusMessage", event, transactionSensitive = true, deliverySensitive = true)
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun givenNewMLSMessageEvent_whenOnEventInvoked_thenNewMLSMessageEventHandlerShouldBeCalled() = runTest {
        val event = Events.newMLSMessage()
        val arrangement = Arrangement()

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("newMLSMessage", event, transactionSensitive = true, deliverySensitive = true)
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun givenNewConversationEvent_whenOnEventInvoked_thenNewConversationHandlerShouldBeCalled() = runTest {
        val event = Events.newConversation()
        val arrangement = Arrangement()

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("newConversation", event, transactionSensitive = true)
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun givenDeletedConversationEvent_whenOnEventInvoked_thenDeletedConversationHandlerShouldBeCalled() = runTest {
        val event = Events.deletedConversation()
        val arrangement = Arrangement()

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("deletedConversation", event, transactionSensitive = true)
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun givenMemberJoinEvent_whenOnEventInvoked_thenMemberJoinHandlerShouldBeCalled() = runTest {
        val event = Events.memberJoin()
        val arrangement = Arrangement()
        val expected = arrangement.memberJoinResult

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("memberJoin", event, transactionSensitive = true)
        assertSame(expected, result)
    }

    @Test
    fun givenMemberLeaveEvent_whenOnEventInvoked_thenPropagateMemberLeaveHandlerResult() = runTest {
        val event = Events.memberLeave()
        val arrangement = Arrangement()
        val expected = arrangement.memberLeaveResult

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("memberLeave", event, transactionSensitive = true)
        assertSame(expected, result)
    }

    @Test
    fun givenMemberChangeEvent_whenOnEventInvoked_thenMemberChangeHandlerShouldBeCalled() = runTest {
        val event = Events.memberChange()
        val arrangement = Arrangement()

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("memberChange", event, transactionSensitive = true)
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun givenMLSWelcomeEvent_whenOnEventInvoked_thenMlsWelcomeHandlerShouldBeCalled() = runTest {
        val event = Events.mlsWelcome()
        val arrangement = Arrangement()

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("mlsWelcome", event, transactionSensitive = true)
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun givenRenamedConversationEvent_whenOnEventInvoked_thenRenamedConversationHandlerShouldBeCalled() = runTest {
        val event = Events.renamedConversation()
        val arrangement = Arrangement()

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("renamedConversation", event)
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun givenConversationReceiptModeEvent_whenOnEventInvoked_thenReceiptModeUpdateEventHandlerShouldBeCalled() = runTest {
        val event = Events.receiptModeUpdate()
        val arrangement = Arrangement()

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("receiptMode", event)
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun givenConversationMessageTimerEvent_whenOnEventInvoked_thenPropagateConversationMessageTimerEventHandlerResult() = runTest {
        val event = Events.timerChanged()
        val expected: Either<CoreFailure, Unit> = Either.Left(FAILURE)
        val arrangement = Arrangement().apply { timerResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("messageTimer", event)
        assertSame(expected, result)
    }

    @Test
    fun givenCodeUpdateEventAndHandlingSuccess_whenOnEventInvoked_thenPropagateCodeUpdatedHandlerResult() = runTest {
        val event = Events.codeUpdated()
        val expected: Either<StorageFailure, Unit> = Either.Right(Unit)
        val arrangement = Arrangement().apply { codeUpdatedResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("codeUpdated", event)
        assertSame(expected, result)
    }

    @Test
    fun givenCodeUpdateEventAndHandlingFail_whenOnEventInvoked_thenPropagateCodeUpdatedHandlerResult() = runTest {
        val event = Events.codeUpdated()
        val expected: Either<StorageFailure, Unit> = Either.Left(StorageFailure.DataNotFound)
        val arrangement = Arrangement().apply { codeUpdatedResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("codeUpdated", event)
        assertSame(expected, result)
    }

    @Test
    fun givenCodeDeleteEventAndHandlingSuccess_whenOnEventInvoked_thenPropagateCodeUpdatedHandlerResult() = runTest {
        val event = Events.codeDeleted()
        val expected: Either<StorageFailure, Unit> = Either.Right(Unit)
        val arrangement = Arrangement().apply { codeDeletedResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("codeDeleted", event)
        assertSame(expected, result)
    }

    @Test
    fun givenCodeDeleteEventAndHandlingFail_whenOnEventInvoked_thenPropagateCodeUpdatedHandlerResult() = runTest {
        val event = Events.codeDeleted()
        val expected: Either<StorageFailure, Unit> = Either.Left(StorageFailure.DataNotFound)
        val arrangement = Arrangement().apply { codeDeletedResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("codeDeleted", event)
        assertSame(expected, result)
    }

    @Test
    fun givenTypingEventAndHandlingSucceeds_whenOnEventInvoked_thenSuccessHandlerResult() = runTest {
        val event = Events.typingIndicator()
        val expected: Either<StorageFailure, Unit> = Either.Right(Unit)
        val arrangement = Arrangement().apply { typingResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("typing", event)
        assertSame(expected, result)
    }

    @Test
    fun givenTypingEventAndHandlingFails_whenOnEventInvoked_thenSuccessHandlerPropagateFails() = runTest {
        val event = Events.typingIndicator()
        val expected: Either<StorageFailure, Unit> = Either.Left(StorageFailure.Generic(IllegalStateException("typing")))
        val arrangement = Arrangement().apply { typingResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("typing", event)
        assertSame(expected, result)
    }

    @Test
    fun givenAccessUpdateEventAndHandlingSucceeds_whenOnEventInvoked_thenSuccessHandlerResult() = runTest {
        val event = Events.accessUpdate()
        val expected: Either<StorageFailure, Unit> = Either.Right(Unit)
        val arrangement = Arrangement().apply { accessResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("access", event)
        assertSame(expected, result)
    }

    @Test
    fun givenAccessUpdateEventAndHandlingFails_whenOnEventInvoked_thenHandlerPropagateFails() = runTest {
        val event = Events.accessUpdate()
        val expected: Either<StorageFailure, Unit> = Either.Left(StorageFailure.Generic(IllegalStateException("access")))
        val arrangement = Arrangement().apply { accessResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("access", event)
        assertSame(expected, result)
    }

    @Test
    fun givenMemberJoinHandlerReturnsLeft_whenOnEventInvoked_thenTheSameLeftIsPropagated() = runTest {
        val event = Events.memberJoin()
        val expected: Either<CoreFailure, Unit> = Either.Left(FAILURE)
        val arrangement = Arrangement().apply { memberJoinResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("memberJoin", event, transactionSensitive = true)
        assertSame(expected, result)
    }

    @Test
    fun givenMemberLeaveHandlerReturnsLeft_whenOnEventInvoked_thenTheSameLeftIsPropagated() = runTest {
        val event = Events.memberLeave()
        val expected: Either<CoreFailure, Unit> = Either.Left(FAILURE)
        val arrangement = Arrangement().apply { memberLeaveResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("memberLeave", event, transactionSensitive = true)
        assertSame(expected, result)
    }

    @Test
    fun givenMLSWelcomeHandlerReturnsLeft_whenOnEventInvoked_thenTheResultIsIgnored() = runTest {
        val event = Events.mlsWelcome()
        val arrangement = Arrangement().apply { mlsWelcomeResult = Either.Left(FAILURE) }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("mlsWelcome", event, transactionSensitive = true)
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun givenProtocolUpdateHandlerReturnsLeft_whenOnEventInvoked_thenTheSameLeftIsPropagated() = runTest {
        val event = Events.protocolUpdate()
        val expected: Either<CoreFailure, Unit> = Either.Left(FAILURE)
        val arrangement = Arrangement().apply { protocolResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("protocol", event, transactionSensitive = true)
        assertSame(expected, result)
    }

    @Test
    fun givenChannelAddPermissionHandlerReturnsLeft_whenOnEventInvoked_thenTheSameLeftIsPropagated() = runTest {
        val event = Events.channelAddPermissionUpdate()
        val expected: Either<CoreFailure, Unit> = Either.Left(FAILURE)
        val arrangement = Arrangement().apply { channelAddPermissionResult = expected }

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("channelAddPermission", event)
        assertSame(expected, result)
    }

    @Test
    fun givenMLSResetEvent_whenOnEventInvoked_thenTheHandlerIsCalledAndItsResultIsIgnored() = runTest {
        val event = Events.mlsReset()
        val arrangement = Arrangement()

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        arrangement.assertRouted("mlsReset", event, transactionSensitive = true)
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun givenProtocolUpdateEvent_whenOnEventInvoked_thenProtocolHandlerResultIsPropagated() = runTest {
        val event = Events.protocolUpdate()
        val arrangement = Arrangement()
        val expected = arrangement.protocolResult

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        assertSame(expected, result)
        arrangement.assertRouted("protocol", event, transactionSensitive = true)
    }

    @Test
    fun givenChannelAddPermissionEvent_whenOnEventInvoked_thenChannelHandlerResultIsPropagated() = runTest {
        val event = Events.channelAddPermissionUpdate()
        val arrangement = Arrangement()
        val expected = arrangement.channelAddPermissionResult

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        assertSame(expected, result)
        arrangement.assertRouted("channelAddPermission", event)
    }

    @Test
    fun givenMlsResetEvent_whenOnEventInvoked_thenMlsResetHandlerIsCalled() = runTest {
        val event = Events.mlsReset()
        val arrangement = Arrangement()

        val result = arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)

        assertEquals(Either.Right(Unit), result)
        arrangement.assertRouted("mlsReset", event, transactionSensitive = true)
    }

    @Test
    fun givenPendingMessageSideEffects_whenFlushing_thenNewMessageHandlerIsFlushed() = runTest {
        val arrangement = Arrangement()

        val result = arrangement.receiver.flushPendingSideEffects()

        assertEquals(Either.Right(Unit), result)
        assertEquals(1, arrangement.flushCalls)
    }

    @Test
    fun givenPendingSideEffects_whenFlushInvoked_thenNewMessageHandlerIsCalledOnceAndSuccessIsReturned() = runTest {
        val arrangement = Arrangement()

        val result = arrangement.receiver.flushPendingSideEffects()

        assertEquals(1, arrangement.flushCalls)
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun givenHandlerThrows_whenOnEventInvoked_thenOrdinaryAndCancellationExceptionsPropagateUnchanged() = runTest {
        listOf(IllegalStateException("handler failed"), CancellationException("handler cancelled")).forEach { expected ->
            val event = Events.codeUpdated()
            val arrangement = Arrangement().apply { codeUpdatedThrowable = expected }

            val actual = catchThrowable {
                arrangement.receiver.onEvent(arrangement.transactionContext, event, arrangement.deliveryInfo)
            }

            arrangement.assertRouted("codeUpdated", event)
            assertSame(expected, actual)
        }
    }

    @Test
    fun givenFlushThrows_whenFlushInvoked_thenOrdinaryAndCancellationExceptionsPropagateUnchanged() = runTest {
        listOf(IllegalStateException("flush failed"), CancellationException("flush cancelled")).forEach { expected ->
            val arrangement = Arrangement().apply { flushThrowable = expected }

            val actual = catchThrowable { arrangement.receiver.flushPendingSideEffects() }

            assertEquals(1, arrangement.flushCalls)
            assertSame(expected, actual)
        }
    }

    private class Arrangement {
        val transactionContext = mock<CryptoTransactionContext>()
        val deliveryInfo = EventDeliveryInfo(EventSource.LIVE)
        val calls = mutableListOf<RoutedCall>()
        var flushCalls = 0

        var memberJoinResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var memberLeaveResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var mlsWelcomeResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var timerResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var codeUpdatedResult: Either<StorageFailure, Unit> = Either.Right(Unit)
        var codeDeletedResult: Either<StorageFailure, Unit> = Either.Right(Unit)
        var typingResult: Either<StorageFailure, Unit> = Either.Right(Unit)
        var protocolResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var channelAddPermissionResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var accessResult: Either<StorageFailure, Unit> = Either.Right(Unit)
        var codeUpdatedThrowable: Throwable? = null
        var flushThrowable: Throwable? = null

        private val newMessageHandler = mock<NewMessageEventHandler> {
            everySuspend { handleNewProteusMessage(any(), any(), any()) } calls {
                recordMessage("newProteusMessage", it.args)
            }
            everySuspend { handleNewMLSMessage(any(), any(), any()) } calls {
                recordMessage("newMLSMessage", it.args)
            }
            everySuspend { flushPendingSideEffects() } calls {
                flushCalls++
                flushThrowable?.let { throwable -> throw throwable }
            }
        }
        private val newConversationHandler = mock<NewConversationEventHandler> {
            everySuspend { handle(any(), any()) } calls { recordTransaction("newConversation", it.args) }
        }
        private val deletedConversationHandler = mock<DeletedConversationEventHandler> {
            everySuspend { handle(any(), any()) } calls { recordTransaction("deletedConversation", it.args) }
        }
        private val memberJoinHandler = mock<MemberJoinEventHandler> {
            everySuspend { handle(any(), any()) } calls {
                recordTransaction("memberJoin", it.args)
                memberJoinResult
            }
        }
        private val memberLeaveHandler = mock<MemberLeaveEventHandler> {
            everySuspend { handle(any(), any()) } calls {
                recordTransaction("memberLeave", it.args)
                memberLeaveResult
            }
        }
        private val memberChangeHandler = mock<MemberChangeEventHandler> {
            everySuspend { handle(any(), any()) } calls { recordTransaction("memberChange", it.args) }
        }
        private val mlsWelcomeHandler = mock<MLSWelcomeEventHandler> {
            everySuspend { handle(any(), any()) } calls {
                recordTransaction("mlsWelcome", it.args)
                mlsWelcomeResult
            }
        }
        private val renamedConversationHandler = mock<RenamedConversationEventHandler> {
            everySuspend { handle(any()) } calls { recordEvent("renamedConversation", it.args) }
        }
        private val receiptModeUpdateEventHandler = mock<ReceiptModeUpdateEventHandler> {
            everySuspend { handle(any()) } calls { recordEvent("receiptMode", it.args) }
        }
        private val conversationMessageTimerEventHandler = mock<ConversationMessageTimerEventHandler> {
            everySuspend { handle(any()) } calls {
                recordEvent("messageTimer", it.args)
                timerResult
            }
        }
        private val codeUpdatedHandler = mock<CodeUpdatedHandler> {
            everySuspend { handle(any()) } calls {
                recordEvent("codeUpdated", it.args)
                codeUpdatedThrowable?.let { throwable -> throw throwable }
                codeUpdatedResult
            }
        }
        private val codeDeletedHandler = mock<CodeDeletedHandler> {
            everySuspend { handle(any()) } calls {
                recordEvent("codeDeleted", it.args)
                codeDeletedResult
            }
        }
        private val typingIndicatorHandler = mock<TypingIndicatorHandler> {
            everySuspend { handle(any()) } calls {
                recordEvent("typing", it.args)
                typingResult
            }
        }
        private val protocolUpdateEventHandler = mock<ProtocolUpdateEventHandler> {
            everySuspend { handle(any(), any()) } calls {
                recordTransaction("protocol", it.args)
                protocolResult
            }
        }
        private val channelAddPermissionUpdateEventHandler = mock<ChannelAddPermissionUpdateEventHandler> {
            everySuspend { handle(any()) } calls {
                recordEvent("channelAddPermission", it.args)
                channelAddPermissionResult
            }
        }
        private val accessUpdateEventHandler = mock<AccessUpdateEventHandler> {
            everySuspend { handle(any()) } calls {
                recordEvent("access", it.args)
                accessResult
            }
        }
        private val mlsResetConversationEventHandler = mock<MLSResetConversationEventHandler> {
            everySuspend { handle(any(), any()) } calls { recordTransaction("mlsReset", it.args) }
        }

        val receiver: ConversationEventReceiver = ConversationEventReceiverImpl(
            newMessageHandler = newMessageHandler,
            newConversationHandler = newConversationHandler,
            deletedConversationHandler = deletedConversationHandler,
            memberJoinHandler = memberJoinHandler,
            memberLeaveHandler = memberLeaveHandler,
            memberChangeHandler = memberChangeHandler,
            mlsWelcomeHandler = mlsWelcomeHandler,
            renamedConversationHandler = renamedConversationHandler,
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

        fun assertRouted(
            route: String,
            event: Event.Conversation,
            transactionSensitive: Boolean = false,
            deliverySensitive: Boolean = false,
        ) {
            val call = calls.single { it.route == route }
            assertSame(event, call.event)
            if (transactionSensitive) {
                assertSame(transactionContext, call.transactionContext)
            } else {
                assertNull(call.transactionContext)
            }
            if (deliverySensitive) {
                assertSame(deliveryInfo, call.deliveryInfo)
            } else {
                assertNull(call.deliveryInfo)
            }
        }

        private fun recordMessage(route: String, args: List<Any?>) {
            calls += RoutedCall(
                route = route,
                transactionContext = args[0] as CryptoTransactionContext,
                event = args[1] as Event.Conversation,
                deliveryInfo = args[2] as EventDeliveryInfo,
            )
        }

        private fun recordTransaction(route: String, args: List<Any?>) {
            calls += RoutedCall(
                route = route,
                transactionContext = args[0] as CryptoTransactionContext,
                event = args[1] as Event.Conversation,
            )
        }

        private fun recordEvent(route: String, args: List<Any?>) {
            calls += RoutedCall(route = route, event = args[0] as Event.Conversation)
        }
    }

    private data class RoutedCall(
        val route: String,
        val event: Event.Conversation,
        val transactionContext: CryptoTransactionContext? = null,
        val deliveryInfo: EventDeliveryInfo? = null,
    )

    private object Events {
        private val conversationId = ConversationId("conversation-id", "wire.example")
        private val senderUserId = UserId("sender-id", "wire.example")
        private val otherUserId = UserId("other-id", "wire.example")
        private val instant = Instant.parse("2026-08-21T12:00:00Z")

        fun newMessage() = Event.Conversation.NewMessage(
            id = "new-message-event",
            conversationId = conversationId,
            senderUserId = senderUserId,
            senderClientId = ClientId("sender-client"),
            messageInstant = instant,
            content = "encrypted-content",
            encryptedExternalContent = null,
        )

        fun newMLSMessage() = Event.Conversation.NewMLSMessage(
            id = "new-mls-message-event",
            conversationId = conversationId,
            subconversationId = null,
            senderUserId = senderUserId,
            messageInstant = instant,
            content = "encrypted-content",
        )

        fun newConversation() = Event.Conversation.NewConversation(
            id = "new-conversation-event",
            conversationId = conversationId,
            senderUserId = senderUserId,
            dateTime = instant,
            conversation = ConversationResponse(
                creator = null,
                members = ConversationMembersResponse(self = null, otherMembers = emptyList()),
                name = null,
                id = NetworkQualifiedID(conversationId.value, conversationId.domain),
                groupId = null,
                epoch = null,
                type = ConversationResponse.Type.GROUP,
                messageTimer = null,
                teamId = null,
                protocol = ConvProtocol.PROTEUS,
                lastEventTime = instant.toString(),
                mlsCipherSuiteTag = null,
                access = emptySet(),
                accessRole = null,
                receiptMode = ReceiptMode.DISABLED,
            ),
        )

        fun deletedConversation() = Event.Conversation.DeletedConversation(
            id = "deleted-conversation-event",
            conversationId = conversationId,
            senderUserId = senderUserId,
            dateTime = instant,
        )

        fun memberJoin() = Event.Conversation.MemberJoin(
            id = "member-join-event",
            conversationId = conversationId,
            addedBy = senderUserId,
            members = listOf(Conversation.Member(otherUserId, Conversation.Member.Role.Member)),
            dateTime = instant,
        )

        fun memberLeave() = Event.Conversation.MemberLeave(
            id = "member-leave-event",
            conversationId = conversationId,
            removedBy = senderUserId,
            removedList = listOf(otherUserId),
            dateTime = instant,
            reason = MemberLeaveReason.Left,
        )

        fun memberChange() = Event.Conversation.MemberChanged.MemberChangedRole(
            id = "member-change-event",
            conversationId = conversationId,
            dateTime = instant,
            member = Conversation.Member(otherUserId, Conversation.Member.Role.Admin),
        )

        fun mlsWelcome() = Event.Conversation.MLSWelcome(
            id = "mls-welcome-event",
            conversationId = conversationId,
            senderUserId = senderUserId,
            message = "welcome-message",
        )

        fun renamedConversation() = Event.Conversation.RenamedConversation(
            id = "rename-event",
            conversationId = conversationId,
            conversationName = "renamed conversation",
            senderUserId = senderUserId,
            dateTime = instant,
        )

        fun receiptModeUpdate() = Event.Conversation.ConversationReceiptMode(
            id = "receipt-mode-event",
            conversationId = conversationId,
            receiptMode = Conversation.ReceiptMode.ENABLED,
            senderUserId = senderUserId,
        )

        fun timerChanged() = Event.Conversation.ConversationMessageTimer(
            id = "timer-event",
            conversationId = conversationId,
            messageTimer = 3_000,
            senderUserId = senderUserId,
            dateTime = instant,
        )

        fun codeUpdated() = Event.Conversation.CodeUpdated(
            id = "code-updated-event",
            conversationId = conversationId,
            key = "key",
            code = "code",
            uri = "uri",
            isPasswordProtected = false,
        )

        fun codeDeleted() = Event.Conversation.CodeDeleted(
            id = "code-deleted-event",
            conversationId = conversationId,
        )

        fun typingIndicator() = Event.Conversation.TypingIndicator(
            id = "typing-event",
            conversationId = conversationId,
            senderUserId = senderUserId,
            typingIndicatorMode = Conversation.TypingIndicatorMode.STARTED,
        )

        fun accessUpdate() = Event.Conversation.AccessUpdate(
            id = "access-event",
            conversationId = conversationId,
            access = setOf(Conversation.Access.PRIVATE),
            accessRole = setOf(Conversation.AccessRole.TEAM_MEMBER),
            qualifiedFrom = senderUserId,
        )

        fun protocolUpdate() = Event.Conversation.ConversationProtocol(
            id = "protocol-event",
            conversationId = conversationId,
            protocol = Conversation.Protocol.MIXED,
            senderUserId = senderUserId,
        )

        fun channelAddPermissionUpdate() = Event.Conversation.ConversationChannelAddPermission(
            id = "channel-add-permission-event",
            conversationId = conversationId,
            channelAddPermission = ConversationDetails.Group.Channel.ChannelAddPermission.ADMINS,
            senderUserId = senderUserId,
        )

        fun mlsReset() = Event.Conversation.MLSReset(
            id = "mls-reset-event",
            conversationId = conversationId,
            from = senderUserId,
            groupID = GroupID("old-group"),
            newGroupID = GroupID("new-group"),
        )
    }

    private companion object {
        val FAILURE = CoreFailure.MissingClientRegistration

        suspend fun catchThrowable(block: suspend () -> Unit): Throwable = try {
            block()
            error("Expected block to throw")
        } catch (throwable: Throwable) {
            throwable
        }
    }
}
