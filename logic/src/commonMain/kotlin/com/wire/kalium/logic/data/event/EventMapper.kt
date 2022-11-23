package com.wire.kalium.logic.data.event

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.logic.data.connection.ConnectionMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRoleMapper
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.featureConfig.FeatureConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.notification.EventResponse
import com.wire.kalium.network.api.base.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.base.model.getPreviewAssetOrNull
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray

@Suppress("TooManyFunctions")
class EventMapper(
    private val idMapper: IdMapper,
    private val memberMapper: MemberMapper,
    private val connectionMapper: ConnectionMapper,
    private val featureConfigMapper: FeatureConfigMapper,
    private val roleMapper: ConversationRoleMapper,
    private val userTypeMapper: DomainUserTypeMapper,
) {
    fun fromDTO(eventResponse: EventResponse): List<Event> {
        // TODO(edge-case): Multiple payloads in the same event have the same ID, is this an issue when marking lastProcessedEventId?
        val id = eventResponse.id
        return eventResponse.payload?.map { eventContentDTO ->
            fromEventContentDTO(id, eventContentDTO, eventResponse.transient)
        } ?: listOf()
    }

    @Suppress("ComplexMethod")
    fun fromEventContentDTO(id: String, eventContentDTO: EventContentDTO, transient: Boolean): Event =
        when (eventContentDTO) {
            is EventContentDTO.Conversation.NewMessageDTO -> newMessage(id, eventContentDTO, transient)
            is EventContentDTO.Conversation.NewConversationDTO -> newConversation(id, eventContentDTO, transient)
            is EventContentDTO.Conversation.MemberJoinDTO -> conversationMemberJoin(id, eventContentDTO, transient)
            is EventContentDTO.Conversation.MemberLeaveDTO -> conversationMemberLeave(id, eventContentDTO, transient)
            is EventContentDTO.Conversation.MemberUpdateDTO -> memberUpdate(id, eventContentDTO, transient)
            is EventContentDTO.Conversation.MLSWelcomeDTO -> welcomeMessage(id, eventContentDTO, transient)
            is EventContentDTO.Conversation.NewMLSMessageDTO -> newMLSMessage(id, eventContentDTO, transient)
            is EventContentDTO.User.NewConnectionDTO -> connectionUpdate(id, eventContentDTO, transient)
            is EventContentDTO.User.ClientRemoveDTO -> clientRemove(id, eventContentDTO, transient)
            is EventContentDTO.User.UserDeleteDTO -> userDelete(id, eventContentDTO, transient)
            is EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO -> featureConfig(id, eventContentDTO, transient)
            is EventContentDTO.User.NewClientDTO, EventContentDTO.Unknown -> Event.Unknown(id, transient)
            is EventContentDTO.Conversation.AccessUpdate -> Event.Unknown(id, transient) // TODO: update it after logic code is merged
            is EventContentDTO.Conversation.DeletedConversationDTO -> conversationDeleted(id, eventContentDTO, transient)
            is EventContentDTO.Conversation.ConversationRenameDTO -> conversationRenamed(id, eventContentDTO, transient)
            is EventContentDTO.Team.MemberJoin -> teamMemberJoined(id, eventContentDTO, transient)
            is EventContentDTO.Team.MemberLeave -> teamMemberLeft(id, eventContentDTO, transient)
            is EventContentDTO.Team.MemberUpdate -> teamMemberUpdate(id, eventContentDTO, transient)
            is EventContentDTO.Team.Update -> teamUpdate(id, eventContentDTO, transient)
            is EventContentDTO.User.UpdateDTO -> userUpdate(id, eventContentDTO, transient)
        }

    private fun welcomeMessage(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MLSWelcomeDTO,
        transient: Boolean
    ) = Event.Conversation.MLSWelcome(
        id,
        idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        transient,
        idMapper.fromApiModel(eventContentDTO.qualifiedFrom),
        eventContentDTO.message,
    )

    private fun newMessage(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.NewMessageDTO,
        transient: Boolean
    ) = Event.Conversation.NewMessage(
        id,
        idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        transient,
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
        eventContentDTO: EventContentDTO.Conversation.NewMLSMessageDTO,
        transient: Boolean
    ) = Event.Conversation.NewMLSMessage(
        id,
        idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        transient,
        idMapper.fromApiModel(eventContentDTO.qualifiedFrom),
        eventContentDTO.time,
        eventContentDTO.message
    )

    private fun connectionUpdate(
        id: String,
        eventConnectionDTO: EventContentDTO.User.NewConnectionDTO,
        transient: Boolean
    ) = Event.User.NewConnection(
        transient,
        id,
        connectionMapper.fromApiToModel(eventConnectionDTO.connection)
    )

    private fun userDelete(id: String, eventUserDelete: EventContentDTO.User.UserDeleteDTO, transient: Boolean): Event.User.UserDelete {
        return Event.User.UserDelete(transient, id, idMapper.fromApiModel(eventUserDelete.userId))
    }

    private fun clientRemove(id: String, eventClientRemove: EventContentDTO.User.ClientRemoveDTO, transient: Boolean): Event.User.ClientRemove {
        return Event.User.ClientRemove(transient, id, ClientId(eventClientRemove.client.clientId))
    }

    private fun newConversation(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.NewConversationDTO,
        transient: Boolean
    ) = Event.Conversation.NewConversation(
        id,
        idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        transient,
        eventContentDTO.time,
        eventContentDTO.data
    )

    fun conversationMemberJoin(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberJoinDTO,
        transient: Boolean
    ) = Event.Conversation.MemberJoin(
        id = id,
        conversationId = idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        addedBy = idMapper.fromApiModel(eventContentDTO.qualifiedFrom),
        members = eventContentDTO.members.users.map { memberMapper.fromApiModel(it) },
        timestampIso = eventContentDTO.time,
        transient = transient
    )

    fun conversationMemberLeave(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberLeaveDTO,
        transient: Boolean
    ) = Event.Conversation.MemberLeave(
        id = id,
        conversationId = idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
        removedBy = idMapper.fromApiModel(eventContentDTO.qualifiedFrom),
        removedList = eventContentDTO.members.qualifiedUserIds.map { idMapper.fromApiModel(it) },
        timestampIso = eventContentDTO.time,
        transient = transient
    )

    private fun memberUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberUpdateDTO,
        transient: Boolean
    ): Event.Conversation.MemberChanged {
        return when {
            eventContentDTO.roleChange.role?.isNotEmpty() == true -> {
                Event.Conversation.MemberChanged.MemberChangedRole(
                    id = id,
                    conversationId = idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
                    timestampIso = eventContentDTO.time,
                    transient = transient,
                    member = Conversation.Member(
                        id = idMapper.fromApiModel(eventContentDTO.roleChange.qualifiedUserId),
                        role = roleMapper.fromApi(eventContentDTO.roleChange.role.orEmpty())
                    ),
                )
            }

            eventContentDTO.roleChange.mutedStatus != null -> {
                Event.Conversation.MemberChanged.MemberMutedStatusChanged(
                    id = id,
                    conversationId = idMapper.fromApiModel(eventContentDTO.qualifiedConversation),
                    timestampIso = eventContentDTO.time,
                    mutedConversationChangedTime = eventContentDTO.roleChange.mutedRef.orEmpty(),
                    transient = transient,
                    mutedConversationStatus = mapConversationMutedStatus(eventContentDTO.roleChange.mutedStatus)
                )
            }

            else -> {
                Event.Conversation.MemberChanged.IgnoredMemberChanged(id, idMapper.fromApiModel(eventContentDTO.qualifiedConversation), transient)
            }
        }
    }

    @Suppress("MagicNumber")
    private fun mapConversationMutedStatus(status: Int?) = when (status) {
        0 -> MutedConversationStatus.AllAllowed
        1 -> MutedConversationStatus.OnlyMentionsAllowed
        3 -> MutedConversationStatus.AllMuted
        else -> MutedConversationStatus.AllAllowed
    }

    private fun featureConfig(
        id: String,
        featureConfigUpdatedDTO: EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO,
        transient: Boolean
    ) = when (featureConfigUpdatedDTO.data) {
        is FeatureConfigData.FileSharing -> Event.FeatureConfig.FileSharingUpdated(
            id,
            transient,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.FileSharing)
        )

        is FeatureConfigData.MLS -> Event.FeatureConfig.MLSUpdated(
            id,
            transient,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.MLS)
        )

        is FeatureConfigData.ClassifiedDomains -> Event.FeatureConfig.ClassifiedDomainsUpdated(
            id,
            transient,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.ClassifiedDomains)
        )

        is FeatureConfigData.ConferenceCalling -> Event.FeatureConfig.ConferenceCallingUpdated(
            id,
            transient,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.ConferenceCalling)
        )

        else -> Event.FeatureConfig.UnknownFeatureUpdated(id, transient)
    }

    private fun conversationDeleted(
        id: String,
        deletedConversationDTO: EventContentDTO.Conversation.DeletedConversationDTO,
        transient: Boolean
    ) = Event.Conversation.DeletedConversation(
        id = id,
        conversationId = idMapper.fromApiModel(deletedConversationDTO.qualifiedConversation),
        senderUserId = idMapper.fromApiModel(deletedConversationDTO.qualifiedFrom),
        transient = transient,
        timestampIso = deletedConversationDTO.time
    )

    private fun conversationRenamed(
        id: String,
        event: EventContentDTO.Conversation.ConversationRenameDTO,
        transient: Boolean
    ) = Event.Conversation.RenamedConversation(
        id = id,
        conversationId = idMapper.fromApiModel(event.qualifiedConversation),
        senderUserId = idMapper.fromApiModel(event.qualifiedFrom),
        conversationName = event.updateNameData.conversationName,
        transient = transient,
        timestampIso = event.time,
    )

    private fun teamMemberJoined(
        id: String,
        event: EventContentDTO.Team.MemberJoin,
        transient: Boolean
    ) = Event.Team.MemberJoin(
        id = id,
        teamId = event.teamId,
        transient = transient,
        memberId = event.teamMember.nonQualifiedUserId
    )

    private fun teamMemberLeft(
        id: String,
        event: EventContentDTO.Team.MemberLeave,
        transient: Boolean
    ) = Event.Team.MemberLeave(
        id = id,
        teamId = event.teamId,
        memberId = event.teamMember.nonQualifiedUserId,
        transient = transient,
        timestampIso = event.time
    )

    private fun teamMemberUpdate(
        id: String,
        event: EventContentDTO.Team.MemberUpdate,
        transient: Boolean
    ) = Event.Team.MemberUpdate(
        id = id,
        teamId = event.teamId,
        memberId = event.permissionsResponse.nonQualifiedUserId,
        transient = transient,
        permissionCode = event.permissionsResponse.permissions.own
    )

    private fun teamUpdate(
        id: String,
        event: EventContentDTO.Team.Update,
        transient: Boolean
    ) = Event.Team.Update(
        id = id,
        teamId = event.teamId,
        icon = event.teamUpdate.icon,
        transient = transient,
        name = event.teamUpdate.name
    )

    private fun userUpdate(
        id: String,
        event: EventContentDTO.User.UpdateDTO,
        transient: Boolean
    ) = Event.User.Update(
        id = id,
        userId = event.userData.nonQualifiedUserId,
        accentId = event.userData.accentId,
        ssoIdDeleted = event.userData.ssoIdDeleted,
        name = event.userData.name,
        handle = event.userData.handle,
        email = event.userData.email,
        previewAssetId = event.userData.assets?.getPreviewAssetOrNull()?.key,
        transient = transient,
        completeAssetId = event.userData.assets?.getCompleteAssetOrNull()?.key
    )

}
