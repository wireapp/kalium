package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.EventReceiver
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler

interface CommitBundleEventReceiver : EventReceiver<Event.Conversation>

class CommitBundleEventReceiverImpl(
    private val memberJoinEventHandler: MemberJoinEventHandler,
    private val memberLeaveEventHandler: MemberLeaveEventHandler
) : CommitBundleEventReceiver {
    override suspend fun onEvent(event: Event.Conversation) {
        when (event) {
            is Event.Conversation.MemberJoin -> memberJoinEventHandler.handle(event)
            is Event.Conversation.MemberLeave -> memberLeaveEventHandler.handle(event)
            else -> kaliumLogger.w("Unexpected event received by commit bundle: $event")
        }
    }
}
