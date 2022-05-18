package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.notification.EventContentDTO
import com.wire.kalium.network.api.notification.EventResponse

class EventMapper(private val idMapper: IdMapper) {

    fun fromDTO(eventResponse: EventResponse): List<Event> {
        // TODO(edge-case): Multiple payloads in the same event have the same ID, is this an issue when marking lastProcessedEventId?
        val id = eventResponse.id
        return eventResponse.payload?.map { eventContentDTO ->
            when (eventContentDTO) {
                is EventContentDTO.Conversation.NewMessageDTO -> newMessage(id, eventContentDTO)
                is EventContentDTO.Conversation.NewConversationDTO -> newConversation(id, eventContentDTO)
                is EventContentDTO.Conversation.MemberJoinDTO -> memberJoin(id, eventContentDTO)
                is EventContentDTO.Conversation.MemberLeaveDTO -> memberLeave(id, eventContentDTO)
                is EventContentDTO.Conversation.MLSWelcomeDTO -> welcomeMessage(id, eventContentDTO)
                is EventContentDTO.Conversation.NewMLSMessageDTO -> newMLSMessage(id, eventContentDTO)
                is EventContentDTO.User.NewClientDTO, EventContentDTO.Unknown -> Event.Unknown(id)
            }
        } ?: listOf()
    }

    private fun welcomeMessage(id: String,
                               eventContentDTO: EventContentDTO.Conversation.MLSWelcomeDTO
    ) = Event.Conversation.MLSWelcome(
        id,
        idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        idMapper.fromApiModel(eventContentDTO.qualifiedFrom),
        eventContentDTO.message
    )

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
    private fun newMLSMessage(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.NewMLSMessageDTO
    ) = Event.Conversation.NewMLSMessage(
        id,
        idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        idMapper.fromApiModel(eventContentDTO.qualifiedFrom),
        eventContentDTO.time,
        eventContentDTO.message
    )


    private fun newConversation(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.NewConversationDTO
    ) = Event.Conversation.NewConversation(
        id,
        idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        eventContentDTO.time,
        eventContentDTO.data
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
