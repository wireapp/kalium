package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MLSWelcomeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberChangeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.NewConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.NewMessageEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler

interface ConversationEventReceiver : EventReceiver<Event.Conversation>

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
    private val renamedConversationHandler: RenamedConversationEventHandler
) : ConversationEventReceiver {
    override suspend fun onEvent(event: Event.Conversation) {
        when (event) {
            is Event.Conversation.NewMessage -> newMessageHandler.handleNewProteusMessage(event)
            is Event.Conversation.NewMLSMessage -> newMessageHandler.handleNewMLSMessage(event)
            is Event.Conversation.NewConversation -> newConversationHandler.handle(event)
            is Event.Conversation.DeletedConversation -> deletedConversationHandler.handle(event)
            is Event.Conversation.MemberJoin -> memberJoinHandler.handle(event)
            is Event.Conversation.MemberLeave -> memberLeaveHandler.handle(event)
            is Event.Conversation.MemberChanged -> memberChangeHandler.handle(event)
            is Event.Conversation.MLSWelcome -> mlsWelcomeHandler.handle(event)
            is Event.Conversation.RenamedConversation -> renamedConversationHandler.handle(event)
            is Event.Conversation.AccessUpdate -> TODO()
        }
    }
}
