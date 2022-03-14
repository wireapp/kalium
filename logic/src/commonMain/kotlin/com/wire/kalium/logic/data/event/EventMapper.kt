package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.notification.EventContentDTO
import com.wire.kalium.network.api.notification.EventResponse

class EventMapper(private val idMapper: IdMapper) {

    fun fromDTO(eventResponse: EventResponse): List<Event> {
        // FIXME: Multiple payloads in the same event have the same ID, is this an issue when marking lastProcessedEventId?
        val id = eventResponse.id
        return eventResponse.payload?.map { eventContentDTO ->
            when (eventContentDTO) {
                is EventContentDTO.Conversation.NewMessageDTO -> newMessage(id, eventContentDTO)
                is EventContentDTO.Conversation.MemberJoinDTO -> memberJoin(id, eventContentDTO)
                is EventContentDTO.Conversation.MemberLeaveDTO -> memberLeave(id, eventContentDTO)
                is EventContentDTO.User.NewClientDTO, EventContentDTO.Unknown -> Event.Unknown(id)
            }
        } ?: listOf()
    }

    private fun newMessage(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.NewMessageDTO
    ) = Event.Conversation.NewMessage(
        id,
        idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        idMapper.fromApiModel(eventContentDTO.qualifiedFrom),
        ClientId(eventContentDTO.data.sender),
        eventContentDTO.time,
        eventContentDTO.data.text
    )

    private fun memberJoin(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberJoinDTO
    ) = Event.Conversation.MemberJoin(
        id,
        idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        idMapper.fromApiModel(eventContentDTO.qualifiedFrom),
        members = eventContentDTO.members,
        from = eventContentDTO.from
    )

    private fun memberLeave(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberLeaveDTO
    ) = Event.Conversation.MemberLeave(
        id,
        idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        idMapper.fromApiModel(eventContentDTO.qualifiedFrom),
        members = eventContentDTO.members,
        from = eventContentDTO.from
    )
}
