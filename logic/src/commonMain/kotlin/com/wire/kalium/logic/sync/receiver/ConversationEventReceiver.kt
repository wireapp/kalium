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
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.AccessUpdateEventHandler
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
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdatedHandler
import com.wire.kalium.logic.sync.receiver.handler.TypingIndicatorHandler

internal interface ConversationEventReceiver : EventReceiver<Event.Conversation>

// Suppressed as it's an old issue
// TODO(refactor): Create a `MessageEventReceiver` to offload some logic from here
@Suppress("LongParameterList", "TooManyFunctions", "ComplexMethod")
internal class ConversationEventReceiverImpl(
    private val newMessageHandler: NewMessageEventHandler,
    private val newConversationHandler: NewConversationEventHandler,
    private val deletedConversationHandler: DeletedConversationEventHandler,
    private val memberJoinHandler: MemberJoinEventHandler,
    private val memberLeaveHandler: MemberLeaveEventHandler,
    private val memberChangeHandler: MemberChangeEventHandler,
    private val mlsWelcomeHandler: MLSWelcomeEventHandler,
    private val renamedConversationHandler: RenamedConversationEventHandler,
    private val receiptModeUpdateEventHandler: ReceiptModeUpdateEventHandler,
    private val conversationMessageTimerEventHandler: ConversationMessageTimerEventHandler,
    private val codeUpdatedHandler: CodeUpdatedHandler,
    private val codeDeletedHandler: CodeDeletedHandler,
    private val typingIndicatorHandler: TypingIndicatorHandler,
    private val protocolUpdateEventHandler: ProtocolUpdateEventHandler,
    private val accessUpdateEventHandler: AccessUpdateEventHandler
) : ConversationEventReceiver {
    override suspend fun onEvent(event: Event.Conversation, deliveryInfo: EventDeliveryInfo): Either<CoreFailure, Unit> {
        // TODO: Make sure errors are accounted for by each handler.
        //       onEvent now requires Either, so we can propagate errors,
        //       but not all handlers are using it yet.
        //       Returning Either.Right is the equivalent of how it was originally working.
        return when (event) {
            is Event.Conversation.NewMessage -> {
                newMessageHandler.handleNewProteusMessage(event, deliveryInfo)
                Either.Right(Unit)
            }

            is Event.Conversation.NewMLSMessage -> {
                newMessageHandler.handleNewMLSMessage(event, deliveryInfo)
                Either.Right(Unit)
            }

            is Event.Conversation.NewConversation -> {
                newConversationHandler.handle(event)
                Either.Right(Unit)
            }

            is Event.Conversation.DeletedConversation -> {
                deletedConversationHandler.handle(event)
                Either.Right(Unit)
            }

            is Event.Conversation.MemberJoin -> memberJoinHandler.handle(event)

            is Event.Conversation.MemberLeave -> memberLeaveHandler.handle(event)

            is Event.Conversation.MemberChanged -> {
                memberChangeHandler.handle(event)
                Either.Right(Unit)
            }

            is Event.Conversation.MLSWelcome -> {
                mlsWelcomeHandler.handle(event)
                Either.Right(Unit)
            }

            is Event.Conversation.RenamedConversation -> {
                renamedConversationHandler.handle(event)
                Either.Right(Unit)
            }

            is Event.Conversation.ConversationReceiptMode -> {
                receiptModeUpdateEventHandler.handle(event)
                Either.Right(Unit)
            }

            is Event.Conversation.AccessUpdate -> accessUpdateEventHandler.handle(event)
            is Event.Conversation.ConversationMessageTimer -> conversationMessageTimerEventHandler.handle(event)
            is Event.Conversation.CodeDeleted -> codeDeletedHandler.handle(event)
            is Event.Conversation.CodeUpdated -> codeUpdatedHandler.handle(event)
            is Event.Conversation.TypingIndicator -> typingIndicatorHandler.handle(event)
            is Event.Conversation.ConversationProtocol -> {
                protocolUpdateEventHandler.handle(event)
            }
        }
    }
}
