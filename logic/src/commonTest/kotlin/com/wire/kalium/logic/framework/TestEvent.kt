package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.event.Event

object TestEvent {

    fun memberJoin(eventId: String = "eventId", members: List<Member> = listOf()) = Event.Conversation.MemberJoin(
        eventId,
        TestConversation.ID,
        TestUser.USER_ID,
        members,
        "2022-03-30T15:36:00.000Z"
    )
}
