package com.wire.kalium.logic.data.event

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.logic.data.connection.ConnectionMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.featureConfig.FeatureConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.network.api.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.notification.EventContentDTO
import com.wire.kalium.network.api.notification.EventResponse
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray

class EventMapper(
    private val idMapper: IdMapper,
    private val memberMapper: MemberMapper,
    private val connectionMapper: ConnectionMapper,
    private val featureConfigMapper: FeatureConfigMapper,
) {
    @Suppress("ComplexMethod")
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
                is EventContentDTO.User.NewConnectionDTO -> connectionUpdate(id, eventContentDTO)
                is EventContentDTO.User.ClientRemoveDTO -> clientRemove(id, eventContentDTO)
                is EventContentDTO.User.UserDeleteDTO -> userDelete(id, eventContentDTO)
                is EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO -> featureConfig(id, eventContentDTO)
                is EventContentDTO.User.NewClientDTO, EventContentDTO.Unknown -> Event.Unknown(id)
                is EventContentDTO.Conversation.AccessUpdate -> Event.Unknown(id) // TODO: update it after logic code is merged
                is EventContentDTO.Conversation.DeletedConversationDTO -> conversationDeleted(id, eventContentDTO)
            }
        } ?: listOf()
    }

    private fun welcomeMessage(
        id: String,
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
        eventContentDTO.data.text,
        eventContentDTO.data.encryptedExternalData?.let {
            EncryptedData(Base64.decodeFromBase64(it.toByteArray(Charsets.UTF_8)))
        }
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

    private fun connectionUpdate(
        id: String,
        eventConnectionDTO: EventContentDTO.User.NewConnectionDTO
    ) = Event.User.NewConnection(
        id,
        connectionMapper.fromApiToModel(eventConnectionDTO.connection)
    )

    private fun userDelete(id: String, eventUserDelete: EventContentDTO.User.UserDeleteDTO): Event.User.UserDelete {
        return Event.User.UserDelete(id, idMapper.fromApiModel(eventUserDelete.userId))
    }

    private fun clientRemove(id: String, eventClientRemove: EventContentDTO.User.ClientRemoveDTO): Event.User.ClientRemove {
        return Event.User.ClientRemove(id, ClientId(eventClientRemove.client.clientId))
    }

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
        id = id,
        conversationId = idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        addedBy = idMapper.fromApiModel(eventContentDTO.qualifiedFrom),
        members = eventContentDTO.members.users.map { memberMapper.fromApiModel(it) },
        timestampIso = eventContentDTO.time
    )

    private fun memberLeave(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberLeaveDTO
    ) = Event.Conversation.MemberLeave(
        id = id,
        conversationId = idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        removedBy = idMapper.fromApiModel(eventContentDTO.qualifiedFrom),
        removedList = eventContentDTO.members.qualifiedUserIds.map { idMapper.fromApiModel(it) },
        timestampIso = eventContentDTO.time
    )

    private fun featureConfig(
        id: String,
        featureConfigUpdatedDTO: EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO
    ) = when (featureConfigUpdatedDTO.data) {
        is FeatureConfigData.FileSharing -> Event.FeatureConfig.FileSharingUpdated(
            id,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.FileSharing)
        )

        is FeatureConfigData.MLS -> Event.FeatureConfig.MLSUpdated(
            id,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.MLS)
        )

        is FeatureConfigData.ClassifiedDomains -> Event.FeatureConfig.ClassifiedDomainsUpdated(
            id,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.ClassifiedDomains)
        )

        else -> Event.FeatureConfig.UnknownFeatureUpdated(id)
    }

    private fun conversationDeleted(
        id: String,
        deletedConversationDTO: EventContentDTO.Conversation.DeletedConversationDTO
    ) = Event.Conversation.DeletedConversation(
        id = id,
        conversationId = idMapper.fromApiModel(deletedConversationDTO.qualifiedConversation),
        senderUserId = idMapper.fromApiModel(deletedConversationDTO.qualifiedFrom),
        timestampIso = deletedConversationDTO.time
    )

}
