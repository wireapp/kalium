/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.connection.ConnectionMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRoleMapper
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.conversation.ReceiptModeMapper
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.event.Event.UserProperty.ReadReceiptModeSet
import com.wire.kalium.logic.data.featureConfig.FeatureConfigMapper
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.toModel
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.notification.EventResponse
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.api.base.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.base.model.getPreviewAssetOrNull
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer

@Suppress("TooManyFunctions")
class EventMapper(
    private val memberMapper: MemberMapper,
    private val connectionMapper: ConnectionMapper,
    private val featureConfigMapper: FeatureConfigMapper,
    private val roleMapper: ConversationRoleMapper,
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper(),
    private val clientMapper: ClientMapper = MapperProvider.clientMapper()
) {
    fun fromDTO(eventResponse: EventResponse, live: Boolean = false): List<Event> {
        // TODO(edge-case): Multiple payloads in the same event have the same ID, is this an issue when marking lastProcessedEventId?
        val id = eventResponse.id
        return eventResponse.payload?.map { eventContentDTO ->
            fromEventContentDTO(id, eventContentDTO, eventResponse.transient, live)
        } ?: listOf()
    }

    @Suppress("ComplexMethod")
    fun fromEventContentDTO(id: String, eventContentDTO: EventContentDTO, transient: Boolean, live: Boolean): Event =
        when (eventContentDTO) {
            is EventContentDTO.Conversation.NewMessageDTO -> newMessage(id, eventContentDTO, transient, live)
            is EventContentDTO.Conversation.NewConversationDTO -> newConversation(id, eventContentDTO, transient, live)
            is EventContentDTO.Conversation.MemberJoinDTO -> conversationMemberJoin(id, eventContentDTO, transient, live)
            is EventContentDTO.Conversation.MemberLeaveDTO -> conversationMemberLeave(id, eventContentDTO, transient, live)
            is EventContentDTO.Conversation.MemberUpdateDTO -> memberUpdate(id, eventContentDTO, transient, live)
            is EventContentDTO.Conversation.MLSWelcomeDTO -> welcomeMessage(id, eventContentDTO, transient, live)
            is EventContentDTO.Conversation.NewMLSMessageDTO -> newMLSMessage(id, eventContentDTO, transient, live)
            is EventContentDTO.User.NewConnectionDTO -> connectionUpdate(id, eventContentDTO, transient, live)
            is EventContentDTO.User.ClientRemoveDTO -> clientRemove(id, eventContentDTO, transient, live)
            is EventContentDTO.User.UserDeleteDTO -> userDelete(id, eventContentDTO, transient, live)
            is EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO -> featureConfig(id, eventContentDTO, transient, live)
            is EventContentDTO.User.NewClientDTO -> newClient(id, eventContentDTO, transient, live)
            is EventContentDTO.Unknown -> unknown(id, transient, live, eventContentDTO)
            is EventContentDTO.Conversation.AccessUpdate -> unknown(id, transient, live, eventContentDTO)
            is EventContentDTO.Conversation.DeletedConversationDTO -> conversationDeleted(id, eventContentDTO, transient, live)
            is EventContentDTO.Conversation.ConversationRenameDTO -> conversationRenamed(id, eventContentDTO, transient, live)
            is EventContentDTO.Team.MemberJoin -> teamMemberJoined(id, eventContentDTO, transient, live)
            is EventContentDTO.Team.MemberLeave -> teamMemberLeft(id, eventContentDTO, transient, live)
            is EventContentDTO.Team.MemberUpdate -> teamMemberUpdate(id, eventContentDTO, transient, live)
            is EventContentDTO.Team.Update -> teamUpdate(id, eventContentDTO, transient, live)
            is EventContentDTO.User.UpdateDTO -> userUpdate(id, eventContentDTO, transient, live)
            is EventContentDTO.UserProperty.PropertiesSetDTO -> updateUserProperties(id, eventContentDTO, transient, live)
            is EventContentDTO.UserProperty.PropertiesDeleteDTO -> deleteUserProperties(id, eventContentDTO, transient, live)
            is EventContentDTO.Conversation.ReceiptModeUpdate -> conversationReceiptModeUpdate(id, eventContentDTO, transient, live)
            is EventContentDTO.Conversation.MessageTimerUpdate -> conversationMessageTimerUpdate(id, eventContentDTO, transient, live)
            is EventContentDTO.Conversation.CodeDeleted -> conversationCodeDeleted(id, eventContentDTO, transient, live)
            is EventContentDTO.Conversation.CodeUpdated -> conversationCodeUpdated(id, eventContentDTO, transient, live)
            is EventContentDTO.Federation -> federationTerminated(id, eventContentDTO, transient, live)
            is EventContentDTO.Conversation.ProtocolUpdate -> conversationProtocolUpdate(id, eventContentDTO, transient, live)
        }

    private fun federationTerminated(id: String, eventContentDTO: EventContentDTO.Federation, transient: Boolean, live: Boolean): Event =
        when (eventContentDTO) {
            is EventContentDTO.Federation.FederationConnectionRemovedDTO -> Event.Federation.ConnectionRemoved(
                id,
                transient,
                live,
                eventContentDTO.domains
            )

            is EventContentDTO.Federation.FederationDeleteDTO -> Event.Federation.Delete(
                id,
                transient,
                live,
                eventContentDTO.domain
            )
        }

    private fun conversationCodeDeleted(
        id: String,
        event: EventContentDTO.Conversation.CodeDeleted,
        transient: Boolean,
        live: Boolean
    ): Event.Conversation.CodeDeleted = Event.Conversation.CodeDeleted(
        id = id,
        transient = transient,
        live = live,
        conversationId = event.qualifiedConversation.toModel()
    )

    private fun conversationCodeUpdated(
        id: String,
        event: EventContentDTO.Conversation.CodeUpdated,
        transient: Boolean,
        live: Boolean
    ): Event.Conversation.CodeUpdated = Event.Conversation.CodeUpdated(
        id = id,
        key = event.data.key,
        code = event.data.code,
        uri = event.data.uri,
        isPasswordProtected = event.data.hasPassword,
        conversationId = event.qualifiedConversation.toModel(),
        transient = transient,
        live = live,
    )

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    fun unknown(
        id: String,
        transient: Boolean,
        live: Boolean,
        eventContentDTO: EventContentDTO,
        cause: String? = null
    ): Event.Unknown = Event.Unknown(
        id = id,
        transient = transient,
        live = live,
        unknownType = when (eventContentDTO) {
            is EventContentDTO.Unknown -> eventContentDTO.type
            else -> try {
                eventContentDTO::class.serializer().descriptor.serialName
            } catch (e: SerializationException) {
                "" // this should never happen, by default serializer returns EventContentDTO.Unknown
            }
        },
        cause = cause
    )

    private fun conversationProtocolUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.ProtocolUpdate,
        transient: Boolean,
        live: Boolean
    ): Event = Event.Conversation.ConversationProtocol(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        transient = transient,
        live = live,
        protocol = eventContentDTO.data.protocol.toModel(),
        senderUserId = eventContentDTO.qualifiedFrom.toModel()
    )

    fun conversationMessageTimerUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MessageTimerUpdate,
        transient: Boolean,
        live: Boolean
    ) = Event.Conversation.ConversationMessageTimer(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        transient = transient,
        live = live,
        messageTimer = eventContentDTO.data.messageTimer,
        senderUserId = eventContentDTO.qualifiedFrom.toModel(),
        timestampIso = eventContentDTO.time
    )

    private fun conversationReceiptModeUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.ReceiptModeUpdate,
        transient: Boolean,
        live: Boolean
    ): Event = Event.Conversation.ConversationReceiptMode(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        transient = transient,
        live = live,
        receiptMode = receiptModeMapper.fromApiToModel(eventContentDTO.data.receiptMode),
        senderUserId = eventContentDTO.qualifiedFrom.toModel()
    )

    private fun updateUserProperties(
        id: String,
        eventContentDTO: EventContentDTO.UserProperty.PropertiesSetDTO,
        transient: Boolean,
        live: Boolean
    ): Event {
        return when (val fieldKeyValue = eventContentDTO.value) {
            is EventContentDTO.FieldKeyNumberValue -> ReadReceiptModeSet(id, transient, live, fieldKeyValue.value == 1)
            is EventContentDTO.FieldUnknownValue -> unknown(
                id = id,
                transient = transient,
                live = live,
                eventContentDTO = eventContentDTO,
                cause = "Unknown value type for key: ${eventContentDTO.key} "
            )
        }
    }

    private fun deleteUserProperties(
        id: String,
        eventContentDTO: EventContentDTO.UserProperty.PropertiesDeleteDTO,
        transient: Boolean,
        live: Boolean
    ): Event {
        return if (PropertiesApi.PropertyKey.WIRE_RECEIPT_MODE.key == eventContentDTO.key) {
            ReadReceiptModeSet(id, transient, live, false)
        } else {
            unknown(
                id = id,
                transient = transient,
                live = live,
                eventContentDTO = eventContentDTO,
                cause = "Unknown key: ${eventContentDTO.key} "
            )
        }
    }

    private fun welcomeMessage(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MLSWelcomeDTO,
        transient: Boolean,
        live: Boolean
    ) = Event.Conversation.MLSWelcome(
        id,
        eventContentDTO.qualifiedConversation.toModel(),
        transient,
        live,
        eventContentDTO.qualifiedFrom.toModel(),
        eventContentDTO.message,
    )

    private fun newMessage(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.NewMessageDTO,
        transient: Boolean,
        live: Boolean
    ) = Event.Conversation.NewMessage(
        id,
        eventContentDTO.qualifiedConversation.toModel(),
        transient,
        live,
        eventContentDTO.qualifiedFrom.toModel(),
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
        transient: Boolean,
        live: Boolean
    ) = Event.Conversation.NewMLSMessage(
        id,
        eventContentDTO.qualifiedConversation.toModel(),
        transient,
        live,
        eventContentDTO.subconversation?.let { SubconversationId(it) },
        eventContentDTO.qualifiedFrom.toModel(),
        eventContentDTO.time,
        eventContentDTO.message
    )

    private fun connectionUpdate(
        id: String,
        eventConnectionDTO: EventContentDTO.User.NewConnectionDTO,
        transient: Boolean,
        live: Boolean
    ) = Event.User.NewConnection(
        transient,
        live,
        id,
        connectionMapper.fromApiToModel(eventConnectionDTO.connection)
    )

    private fun userDelete(id: String, eventUserDelete: EventContentDTO.User.UserDeleteDTO, transient: Boolean, live: Boolean): Event.User.UserDelete {
        return Event.User.UserDelete(transient, live, id, eventUserDelete.userId.toModel())
    }

    private fun clientRemove(
        id: String,
        eventClientRemove: EventContentDTO.User.ClientRemoveDTO,
        transient: Boolean,
        live: Boolean
    ): Event.User.ClientRemove {
        return Event.User.ClientRemove(transient, live, id, ClientId(eventClientRemove.client.clientId))
    }

    private fun newClient(
        id: String,
        eventNewClient: EventContentDTO.User.NewClientDTO,
        transient: Boolean,
        live: Boolean
    ): Event.User.NewClient {
        return Event.User.NewClient(
            transient = transient,
            live = live,
            id = id,
            client = clientMapper.fromClientDto(eventNewClient.client)
        )
    }

    private fun newConversation(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.NewConversationDTO,
        transient: Boolean,
        live: Boolean
    ) = Event.Conversation.NewConversation(
        id,
        eventContentDTO.qualifiedConversation.toModel(),
        transient,
        live,
        eventContentDTO.qualifiedFrom.toModel(),
        eventContentDTO.time,
        eventContentDTO.data
    )

    fun conversationMemberJoin(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberJoinDTO,
        transient: Boolean,
        live: Boolean
    ) = Event.Conversation.MemberJoin(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        addedBy = eventContentDTO.qualifiedFrom.toModel(),
        members = eventContentDTO.members.users.map { memberMapper.fromApiModel(it) },
        timestampIso = eventContentDTO.time,
        transient = transient,
        live = live,
    )

    fun conversationMemberLeave(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberLeaveDTO,
        transient: Boolean,
        live: Boolean
    ) = Event.Conversation.MemberLeave(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        removedBy = eventContentDTO.qualifiedFrom.toModel(),
        removedList = eventContentDTO.members.qualifiedUserIds.map { it.toModel() },
        timestampIso = eventContentDTO.time,
        transient = transient,
        live = live,
    )

    private fun memberUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberUpdateDTO,
        transient: Boolean,
        live: Boolean
    ): Event.Conversation.MemberChanged {
        return when {
            eventContentDTO.roleChange.role?.isNotEmpty() == true -> {
                Event.Conversation.MemberChanged.MemberChangedRole(
                    id = id,
                    conversationId = eventContentDTO.qualifiedConversation.toModel(),
                    timestampIso = eventContentDTO.time,
                    transient = transient,
                    live = live,
                    member = Conversation.Member(
                        id = eventContentDTO.roleChange.qualifiedUserId.toModel(),
                        role = roleMapper.fromApi(eventContentDTO.roleChange.role.orEmpty())
                    ),
                )
            }

            eventContentDTO.roleChange.mutedStatus != null -> {
                Event.Conversation.MemberChanged.MemberMutedStatusChanged(
                    id = id,
                    conversationId = eventContentDTO.qualifiedConversation.toModel(),
                    timestampIso = eventContentDTO.time,
                    mutedConversationChangedTime = eventContentDTO.roleChange.mutedRef.orEmpty(),
                    transient = transient,
                    live = live,
                    mutedConversationStatus = mapConversationMutedStatus(eventContentDTO.roleChange.mutedStatus)
                )
            }

            else -> {
                Event.Conversation.MemberChanged.IgnoredMemberChanged(
                    id,
                    eventContentDTO.qualifiedConversation.toModel(),
                    transient,
                    live
                )
            }
        }
    }

    @Suppress("MagicNumber")
    private fun mapConversationMutedStatus(status: Int?) = when (status) {
        0 -> MutedConversationStatus.AllAllowed
        1 -> MutedConversationStatus.OnlyMentionsAndRepliesAllowed
        3 -> MutedConversationStatus.AllMuted
        else -> MutedConversationStatus.AllAllowed
    }

    private fun featureConfig(
        id: String,
        featureConfigUpdatedDTO: EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO,
        transient: Boolean,
        live: Boolean
    ) = when (featureConfigUpdatedDTO.data) {
        is FeatureConfigData.FileSharing -> Event.FeatureConfig.FileSharingUpdated(
            id,
            transient,
            live,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.FileSharing)
        )

        is FeatureConfigData.SelfDeletingMessages -> Event.FeatureConfig.SelfDeletingMessagesConfig(
            id,
            transient,
            live,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.SelfDeletingMessages)
        )

        is FeatureConfigData.MLS -> Event.FeatureConfig.MLSUpdated(
            id,
            transient,
            live,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.MLS)
        )

        is FeatureConfigData.ClassifiedDomains -> Event.FeatureConfig.ClassifiedDomainsUpdated(
            id,
            transient,
            live,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.ClassifiedDomains)
        )

        is FeatureConfigData.ConferenceCalling -> Event.FeatureConfig.ConferenceCallingUpdated(
            id,
            transient,
            live,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.ConferenceCalling)
        )

        is FeatureConfigData.ConversationGuestLinks -> Event.FeatureConfig.GuestRoomLinkUpdated(
            id,
            transient,
            live,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.ConversationGuestLinks)
        )

        is FeatureConfigData.E2EI -> Event.FeatureConfig.MLSE2EIUpdated(
            id,
            transient,
            live,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.E2EI)
        )

        else -> Event.FeatureConfig.UnknownFeatureUpdated(id, transient, live)
    }

    private fun conversationDeleted(
        id: String,
        deletedConversationDTO: EventContentDTO.Conversation.DeletedConversationDTO,
        transient: Boolean,
        live: Boolean
    ) = Event.Conversation.DeletedConversation(
        id = id,
        conversationId = deletedConversationDTO.qualifiedConversation.toModel(),
        senderUserId = deletedConversationDTO.qualifiedFrom.toModel(),
        transient = transient,
        live = live,
        timestampIso = deletedConversationDTO.time
    )

    fun conversationRenamed(
        id: String,
        event: EventContentDTO.Conversation.ConversationRenameDTO,
        transient: Boolean,
        live: Boolean
    ) = Event.Conversation.RenamedConversation(
        id = id,
        conversationId = event.qualifiedConversation.toModel(),
        senderUserId = event.qualifiedFrom.toModel(),
        conversationName = event.updateNameData.conversationName,
        transient = transient,
        live = live,
        timestampIso = event.time,
    )

    private fun teamMemberJoined(
        id: String,
        event: EventContentDTO.Team.MemberJoin,
        transient: Boolean,
        live: Boolean
    ) = Event.Team.MemberJoin(
        id = id,
        teamId = event.teamId,
        transient = transient,
        live = live,
        memberId = event.teamMember.nonQualifiedUserId
    )

    private fun teamMemberLeft(
        id: String,
        event: EventContentDTO.Team.MemberLeave,
        transient: Boolean,
        live: Boolean
    ) = Event.Team.MemberLeave(
        id = id,
        teamId = event.teamId,
        memberId = event.teamMember.nonQualifiedUserId,
        transient = transient,
        live = live,
        timestampIso = event.time
    )

    private fun teamMemberUpdate(
        id: String,
        event: EventContentDTO.Team.MemberUpdate,
        transient: Boolean,
        live: Boolean
    ) = Event.Team.MemberUpdate(
        id = id,
        teamId = event.teamId,
        memberId = event.permissionsResponse.nonQualifiedUserId,
        transient = transient,
        live = live,
        permissionCode = event.permissionsResponse.permissions.own
    )

    private fun teamUpdate(
        id: String,
        event: EventContentDTO.Team.Update,
        transient: Boolean,
        live: Boolean
    ) = Event.Team.Update(
        id = id,
        teamId = event.teamId,
        icon = event.teamUpdate.icon,
        transient = transient,
        live = live,
        name = event.teamUpdate.name
    )

    private fun userUpdate(
        id: String,
        event: EventContentDTO.User.UpdateDTO,
        transient: Boolean,
        live: Boolean
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
        live = live,
        completeAssetId = event.userData.assets?.getCompleteAssetOrNull()?.key,
        supportedProtocols = event.userData.supportedProtocols?.toModel()
    )

}
