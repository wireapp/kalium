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

package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.connection.ConnectionMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.ConversationRoleMapper
import com.wire.kalium.logic.data.conversation.ReceiptModeMapper
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigMapper
import com.wire.kalium.logic.data.featureConfig.toModel
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.legalhold.LastPreKey
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.toModel
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.sync.receiving.ReceivedPayloadIdentity
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeType
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import kotlin.time.Instant

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class EventMapper(
    private val connectionMapper: ConnectionMapper,
    private val featureConfigMapper: FeatureConfigMapper,
    private val roleMapper: ConversationRoleMapper,
    private val selfUserId: UserId,
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper(),
    private val clientMapper: ClientMapper = MapperProvider.clientMapper(),
    private val qualifiedIdMapper: QualifiedIdMapper = MapperProvider.qualifiedIdMapper(selfUserId),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId)
) {
    internal fun fromDTO(eventResponse: EventResponse, isLive: Boolean): List<EventEnvelope> {
        // TODO(edge-case): Multiple payloads in the same event have the same ID, is this an issue when marking lastProcessedEventId?
        val id = eventResponse.id
        return eventResponse.payload?.mapIndexed { payloadIndex, eventContentDTO ->
            val eventSource = if (isLive) EventSource.LIVE else EventSource.PENDING
            EventEnvelope(
                fromEventContentDTO(id, eventContentDTO, payloadIndex),
                EventDeliveryInfo(eventSource)
            )
        } ?: listOf()
    }

    internal fun fromStoredDTO(eventId: String, payload: List<EventContentDTO>?, isLive: Boolean): List<EventEnvelope> {
        // TODO(edge-case): Multiple payloads in the same event have the same ID, is this an issue when marking lastProcessedEventId?
        val id = eventId

        return payload?.mapIndexed { payloadIndex, eventContentDTO ->
            val eventSource = if (isLive) EventSource.LIVE else EventSource.PENDING
            EventEnvelope(
                fromEventContentDTO(id, eventContentDTO, payloadIndex),
                EventDeliveryInfo(eventSource),
                ReceivedPayloadIdentity(eventId, eventContentDTO),
            )
        } ?: listOf()
    }

    internal companion object {
        /**
         * Full sync acknowledge request for notifications missed.
         */
        val FULL_ACKNOWLEDGE_REQUEST: EventAcknowledgeRequest =
            EventAcknowledgeRequest(type = AcknowledgeType.ACK_FULL_SYNC)
    }

    @Suppress("ComplexMethod")
    internal fun fromEventContentDTO(id: String, eventContentDTO: EventContentDTO, payloadIndex: Int = 0): Event =
        when (eventContentDTO) {
            is EventContentDTO.Conversation.NewMessageDTO -> newMessage(id, eventContentDTO)
            is EventContentDTO.Conversation.NewConversationDTO -> newConversation(id, eventContentDTO)
            is EventContentDTO.Conversation.MemberJoinDTO -> conversationMemberJoin(id, eventContentDTO, payloadIndex)
            is EventContentDTO.Conversation.MemberLeaveDTO -> conversationMemberLeave(id, eventContentDTO, payloadIndex)
            is EventContentDTO.Conversation.MemberUpdateDTO -> memberUpdate(id, eventContentDTO)
            is EventContentDTO.Conversation.MLSWelcomeDTO -> welcomeMessage(id, eventContentDTO)
            is EventContentDTO.Conversation.NewMLSMessageDTO -> newMLSMessage(id, eventContentDTO)
            is EventContentDTO.User.NewConnectionDTO -> connectionUpdate(id, eventContentDTO)
            is EventContentDTO.User.ClientRemoveDTO -> clientRemove(id, eventContentDTO)
            is EventContentDTO.User.UserDeleteDTO -> userDelete(id, eventContentDTO)
            is EventContentDTO.User.NewClientDTO -> newClient(id, eventContentDTO)
            is EventContentDTO.User.NewLegalHoldRequestDTO -> legalHoldRequest(id, eventContentDTO)
            is EventContentDTO.User.LegalHoldEnabledDTO -> legalHoldEnabled(id, eventContentDTO)
            is EventContentDTO.User.LegalHoldDisabledDTO -> legalHoldDisabled(id, eventContentDTO)
            is EventContentDTO.User.SessionRefreshSuggestedDTO -> sessionRefreshSuggested(id)
            is EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO -> featureConfig(featureConfigMapper, id, eventContentDTO)
            is EventContentDTO.Unknown -> unknown(id, eventContentDTO)
            is EventContentDTO.Conversation.AccessUpdate -> conversationAccessUpdate(id, eventContentDTO)
            is EventContentDTO.Conversation.DeletedConversationDTO -> deletedConversation(id, eventContentDTO)
            is EventContentDTO.Conversation.ConversationRenameDTO -> conversationRenamed(id, eventContentDTO, payloadIndex)
            is EventContentDTO.Team.MemberLeave -> teamMemberLeft(id, eventContentDTO)
            is EventContentDTO.User.UpdateDTO -> userUpdate(selfUserId, id, eventContentDTO)
            is EventContentDTO.UserProperty.PropertiesSetDTO -> updateUserProperties(selfUserId, id, eventContentDTO)
            is EventContentDTO.UserProperty.PropertiesDeleteDTO -> deleteUserProperties(id, eventContentDTO)
            is EventContentDTO.Conversation.ReceiptModeUpdate -> conversationReceiptModeUpdate(id, eventContentDTO, payloadIndex)
            is EventContentDTO.Conversation.MessageTimerUpdate -> conversationMessageTimerUpdate(id, eventContentDTO, payloadIndex)
            is EventContentDTO.Conversation.CodeDeleted -> conversationCodeDeleted(id, eventContentDTO)
            is EventContentDTO.Conversation.CodeUpdated -> conversationCodeUpdated(id, eventContentDTO)
            is EventContentDTO.Federation -> federationTerminated(id, eventContentDTO)
            is EventContentDTO.Conversation.ConversationTypingDTO -> conversationTyping(id, eventContentDTO)
            is EventContentDTO.Conversation.ProtocolUpdate -> conversationProtocolUpdate(id, eventContentDTO)
            is EventContentDTO.Conversation.ChannelAddPermissionUpdate -> conversationChannelPermissionUpdate(id, eventContentDTO)
            is EventContentDTO.Conversation.MlsResetConversationDTO -> mlsConversationReset(id, eventContentDTO)
            is EventContentDTO.Conversation.NewMeetingConversationDTO -> newMeetingConversation(id, eventContentDTO)
            is EventContentDTO.Meeting.MeetingCreateDTO -> meetingCreate(id, eventContentDTO)
            is EventContentDTO.Conversation.DeletedMeetingConversationDTO -> deletedMeetingConversation(id, eventContentDTO)
            is EventContentDTO.Meeting.MeetingDeleteDTO -> meetingDelete(id, eventContentDTO)
            is EventContentDTO.Meeting.MeetingUpdateDTO -> meetingUpdate(id, eventContentDTO)
            is EventContentDTO.Meeting.MeetingMemberAddDTO -> meetingMemberAdd(id, eventContentDTO)
        }

    private fun federationTerminated(id: String, eventContentDTO: EventContentDTO.Federation): Event =
        when (eventContentDTO) {
            is EventContentDTO.Federation.FederationConnectionRemovedDTO -> Event.Federation.ConnectionRemoved(
                id,
                eventContentDTO.domains
            )

            is EventContentDTO.Federation.FederationDeleteDTO -> Event.Federation.Delete(
                id,
                eventContentDTO.domain
            )
        }

    internal fun unknown(id: String, eventContentDTO: EventContentDTO, cause: String? = null): Event.Unknown =
        com.wire.kalium.logic.data.event.unknown(id, eventContentDTO, cause)

    private fun conversationProtocolUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.ProtocolUpdate,
    ): Event = Event.Conversation.ConversationProtocol(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        protocol = eventContentDTO.data.protocol.toModel(),
        senderUserId = eventContentDTO.qualifiedFrom.toModel()
    )

    internal fun conversationMessageTimerUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MessageTimerUpdate,
        payloadIndex: Int = 0,
    ) =
        com.wire.kalium.logic.data.event.conversationMessageTimerUpdate(id, eventContentDTO, payloadIndex)

    private fun conversationAccessUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.AccessUpdate
    ): Event = Event.Conversation.AccessUpdate(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        access = conversationMapper.fromApiModelToAccessModel(eventContentDTO.data.access),
        accessRole = conversationMapper.fromApiModelToAccessRoleModel(eventContentDTO.data.accessRole),
        qualifiedFrom = eventContentDTO.qualifiedFrom.toModel()
    )

    private fun conversationReceiptModeUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.ReceiptModeUpdate,
        payloadIndex: Int = 0,
    ) =
        com.wire.kalium.logic.data.event.conversationReceiptModeUpdate(id, eventContentDTO, payloadIndex)

    private fun welcomeMessage(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MLSWelcomeDTO,
    ) = Event.Conversation.MLSWelcome(
        id,
        eventContentDTO.qualifiedConversation.toModel(),
        eventContentDTO.qualifiedFrom.toModel(),
        eventContentDTO.message,
    )

    private fun connectionUpdate(
        id: String,
        eventConnectionDTO: EventContentDTO.User.NewConnectionDTO,
    ) = Event.User.NewConnection(
        id,
        connectionMapper.fromApiToModel(eventConnectionDTO.connection)
    )

    internal fun legalHoldRequest(
        id: String,
        eventContentDTO: EventContentDTO.User.NewLegalHoldRequestDTO
    ): Event.User.LegalHoldRequest {
        return Event.User.LegalHoldRequest(
            id = id,
            clientId = ClientId(eventContentDTO.clientId.clientId),
            lastPreKey = LastPreKey(eventContentDTO.lastPreKey.id, eventContentDTO.lastPreKey.key),
            userId = qualifiedIdMapper.fromStringToQualifiedID(eventContentDTO.id)
        )
    }

    internal fun legalHoldEnabled(
        id: String,
        eventContentDTO: EventContentDTO.User.LegalHoldEnabledDTO
    ): Event.User.LegalHoldEnabled {
        return Event.User.LegalHoldEnabled(
            id,
            qualifiedIdMapper.fromStringToQualifiedID(eventContentDTO.id)
        )
    }

    internal fun legalHoldDisabled(
        id: String,
        eventContentDTO: EventContentDTO.User.LegalHoldDisabledDTO
    ): Event.User.LegalHoldDisabled {
        return Event.User.LegalHoldDisabled(
            id,
            qualifiedIdMapper.fromStringToQualifiedID(eventContentDTO.id)
        )
    }

    private fun sessionRefreshSuggested(id: String): Event.User.SessionRefreshSuggested =
        Event.User.SessionRefreshSuggested(id = id)

    private fun userDelete(
        id: String,
        eventUserDelete: EventContentDTO.User.UserDeleteDTO,
    ): Event.User.UserDelete {
        return Event.User.UserDelete(id, eventUserDelete.userId.toModel())
    }

    private fun clientRemove(
        id: String,
        eventClientRemove: EventContentDTO.User.ClientRemoveDTO,
    ): Event.User.ClientRemove {
        return Event.User.ClientRemove(id, ClientId(eventClientRemove.client.clientId))
    }

    private fun newClient(
        id: String,
        eventNewClient: EventContentDTO.User.NewClientDTO,
    ): Event.User.NewClient {
        return Event.User.NewClient(
            id = id,
            client = clientMapper.fromClientDto(eventNewClient.client)
        )
    }

    private fun newConversation(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.NewConversationDTO,
    ) = com.wire.kalium.logic.data.event.newConversation(id, eventContentDTO)

    internal fun mlsConversationReset(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MlsResetConversationDTO
    ) = Event.Conversation.MLSReset(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        from = eventContentDTO.qualifiedFrom.toModel(),
        groupID = GroupID(eventContentDTO.data.groupId),
        newGroupID = GroupID(eventContentDTO.data.newGroupId),
    )

    private fun memberUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberUpdateDTO,
    ): Event.Conversation.MemberChanged = conversationMemberUpdate(id, eventContentDTO, roleMapper)

    private fun deletedConversation(
        id: String,
        deletedConversationDTO: EventContentDTO.Conversation.DeletedConversationDTO,
    ) = Event.Conversation.DeletedConversation(
        id = id,
        conversationId = deletedConversationDTO.qualifiedConversation.toModel(),
        senderUserId = deletedConversationDTO.qualifiedFrom.toModel(),
        dateTime = Instant.parse(deletedConversationDTO.time)
    )

    private fun deletedMeetingConversation(
        id: String,
        deletedConversationDTO: EventContentDTO.Conversation.DeletedMeetingConversationDTO,
    ) = Event.Conversation.DeletedConversation(
        id = id,
        conversationId = deletedConversationDTO.qualifiedConversation.toModel(),
        senderUserId = deletedConversationDTO.qualifiedFrom.toModel(),
        dateTime = Instant.parse(deletedConversationDTO.time)
    )

    internal fun conversationRenamed(
        id: String,
        event: EventContentDTO.Conversation.ConversationRenameDTO,
        payloadIndex: Int = 0,
    ) =
        com.wire.kalium.logic.data.event.conversationRenamed(id, event, payloadIndex)

    private fun teamMemberLeft(
        id: String,
        event: EventContentDTO.Team.MemberLeave,
    ) = Event.Team.MemberLeave(
        id = id,
        teamId = event.teamId,
        memberId = event.teamMember.nonQualifiedUserId,
        dateTime = event.time
    )

    private fun meetingCreate(
        id: String,
        event: EventContentDTO.Meeting.MeetingCreateDTO,
    ) = Event.Meeting.Create(
        id = id,
        meetingId = event.qualifiedMeetingId.toModel(),
        dateTime = event.time
    )

    private fun meetingDelete(
        id: String,
        event: EventContentDTO.Meeting.MeetingDeleteDTO,
    ) = Event.Meeting.Delete(
        id = id,
        meetingId = event.qualifiedMeetingId.toModel(),
        dateTime = event.time
    )

    private fun meetingUpdate(
        id: String,
        event: EventContentDTO.Meeting.MeetingUpdateDTO,
    ) = Event.Meeting.Update(
        id = id,
        meetingId = event.qualifiedMeetingId.toModel(),
        dateTime = event.time
    )

    private fun meetingMemberAdd(
        id: String,
        event: EventContentDTO.Meeting.MeetingMemberAddDTO,
    ) = Event.Meeting.MemberAdd(
        id = id,
        meetingId = event.qualifiedMeetingId.toModel(),
        dateTime = event.time
    )
}
