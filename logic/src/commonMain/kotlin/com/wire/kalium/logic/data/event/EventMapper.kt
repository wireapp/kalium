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

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.connection.ConnectionMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.ConversationRoleMapper
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.conversation.ReceiptModeMapper
import com.wire.kalium.logic.data.conversation.folders.toFolder
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.event.Event.UserProperty.ReadReceiptModeSet
import com.wire.kalium.logic.data.event.Event.UserProperty.TypingIndicatorModeSet
import com.wire.kalium.logic.data.featureConfig.FeatureConfigMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.legalhold.LastPreKey
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.toModel
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.network.api.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeType
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.MemberLeaveReasonDTO
import com.wire.kalium.network.api.authenticated.properties.PropertyKey.WIRE_RECEIPT_MODE
import com.wire.kalium.network.api.authenticated.properties.PropertyKey.WIRE_TYPING_INDICATOR_MODE
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
class EventMapper(
    private val memberMapper: MemberMapper,
    private val connectionMapper: ConnectionMapper,
    private val featureConfigMapper: FeatureConfigMapper,
    private val roleMapper: ConversationRoleMapper,
    private val selfUserId: UserId,
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper(),
    private val clientMapper: ClientMapper = MapperProvider.clientMapper(),
    private val qualifiedIdMapper: QualifiedIdMapper = MapperProvider.qualifiedIdMapper(selfUserId),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId)
) {
    fun fromDTO(eventResponse: EventResponse, isLive: Boolean): List<EventEnvelope> {
        // TODO(edge-case): Multiple payloads in the same event have the same ID, is this an issue when marking lastProcessedEventId?
        val id = eventResponse.id
        val source = if (isLive) EventSource.LIVE else EventSource.PENDING
        return eventResponse.payload?.map { eventContentDTO ->
            EventEnvelope(
                fromEventContentDTO(id, eventContentDTO),
                EventDeliveryInfo.Legacy(eventResponse.transient, source)
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
    fun fromEventContentDTO(id: String, eventContentDTO: EventContentDTO): Event =
        when (eventContentDTO) {
            is EventContentDTO.Conversation.NewMessageDTO -> newMessage(id, eventContentDTO)
            is EventContentDTO.Conversation.NewConversationDTO -> newConversation(id, eventContentDTO)
            is EventContentDTO.Conversation.MemberJoinDTO -> conversationMemberJoin(id, eventContentDTO)
            is EventContentDTO.Conversation.MemberLeaveDTO -> conversationMemberLeave(id, eventContentDTO)
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
            is EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO -> featureConfig(id, eventContentDTO)
            is EventContentDTO.Unknown -> unknown(id, eventContentDTO)
            is EventContentDTO.Conversation.AccessUpdate -> conversationAccessUpdate(id, eventContentDTO)
            is EventContentDTO.Conversation.DeletedConversationDTO -> conversationDeleted(id, eventContentDTO)
            is EventContentDTO.Conversation.ConversationRenameDTO -> conversationRenamed(id, eventContentDTO)
            is EventContentDTO.Team.MemberLeave -> teamMemberLeft(id, eventContentDTO)
            is EventContentDTO.User.UpdateDTO -> userUpdate(id, eventContentDTO)
            is EventContentDTO.UserProperty.PropertiesSetDTO -> updateUserProperties(id, eventContentDTO)
            is EventContentDTO.UserProperty.PropertiesDeleteDTO -> deleteUserProperties(id, eventContentDTO)
            is EventContentDTO.Conversation.ReceiptModeUpdate -> conversationReceiptModeUpdate(id, eventContentDTO)
            is EventContentDTO.Conversation.MessageTimerUpdate -> conversationMessageTimerUpdate(id, eventContentDTO)
            is EventContentDTO.Conversation.CodeDeleted -> conversationCodeDeleted(id, eventContentDTO)
            is EventContentDTO.Conversation.CodeUpdated -> conversationCodeUpdated(id, eventContentDTO)
            is EventContentDTO.Federation -> federationTerminated(id, eventContentDTO)
            is EventContentDTO.Conversation.ConversationTypingDTO -> conversationTyping(id, eventContentDTO)
            is EventContentDTO.Conversation.ProtocolUpdate -> conversationProtocolUpdate(id, eventContentDTO)
            is EventContentDTO.Conversation.ChannelAddPermissionUpdate -> conversationChannelPermissionUpdate(id, eventContentDTO)
            EventContentDTO.AsyncMissedNotification -> Event.AsyncMissed(id)
        }

    private fun conversationTyping(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.ConversationTypingDTO,
    ): Event =
        Event.Conversation.TypingIndicator(
            id,
            eventContentDTO.qualifiedConversation.toModel(),
            eventContentDTO.qualifiedFrom.toModel(),
            eventContentDTO.time,
            eventContentDTO.status.status.toModel()
        )

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

    private fun conversationCodeDeleted(
        id: String,
        event: EventContentDTO.Conversation.CodeDeleted,
    ): Event.Conversation.CodeDeleted = Event.Conversation.CodeDeleted(
        id = id,
        conversationId = event.qualifiedConversation.toModel()
    )

    private fun conversationCodeUpdated(
        id: String,
        event: EventContentDTO.Conversation.CodeUpdated
    ): Event.Conversation.CodeUpdated = Event.Conversation.CodeUpdated(
        id = id,
        key = event.data.key,
        code = event.data.code,
        uri = event.data.uri,
        isPasswordProtected = event.data.hasPassword,
        conversationId = event.qualifiedConversation.toModel(),
    )

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    fun unknown(
        id: String,
        eventContentDTO: EventContentDTO,
        cause: String? = null
    ): Event.Unknown = Event.Unknown(
        id = id,
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
    ): Event = Event.Conversation.ConversationProtocol(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        protocol = eventContentDTO.data.protocol.toModel(),
        senderUserId = eventContentDTO.qualifiedFrom.toModel()
    )

    private fun conversationChannelPermissionUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.ChannelAddPermissionUpdate,
    ): Event = Event.Conversation.ConversationChannelAddPermission(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        channelAddPermission = eventContentDTO.data.channelAddPermissionTypeDTO.toModel(),
        senderUserId = eventContentDTO.qualifiedFrom.toModel()
    )

    fun conversationMessageTimerUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MessageTimerUpdate,
    ) = Event.Conversation.ConversationMessageTimer(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        messageTimer = eventContentDTO.data.messageTimer,
        senderUserId = eventContentDTO.qualifiedFrom.toModel(),
        dateTime = eventContentDTO.time
    )

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
    ): Event = Event.Conversation.ConversationReceiptMode(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        receiptMode = receiptModeMapper.fromApiToModel(eventContentDTO.data.receiptMode),
        senderUserId = eventContentDTO.qualifiedFrom.toModel()
    )

    private fun updateUserProperties(
        id: String,
        eventContentDTO: EventContentDTO.UserProperty.PropertiesSetDTO,
    ): Event {
        val fieldKeyValue = eventContentDTO.value
        val key = eventContentDTO.key
        return when (fieldKeyValue) {
            is EventContentDTO.FieldKeyNumberValue -> {
                when (key) {
                    WIRE_RECEIPT_MODE.key -> ReadReceiptModeSet(
                        id,
                        fieldKeyValue.value == 1
                    )

                    WIRE_TYPING_INDICATOR_MODE.key -> TypingIndicatorModeSet(
                        id,
                        fieldKeyValue.value != 0
                    )

                    else -> unknown(
                        id = id,
                        eventContentDTO = eventContentDTO,
                        cause = "Unknown key: $key "
                    )
                }
            }

            is EventContentDTO.FieldLabelListValue -> Event.UserProperty.FoldersUpdate(
                id = id,
                folders = fieldKeyValue.value.labels.map { it.toFolder(selfUserId.domain) }
            )

            is EventContentDTO.FieldUnknownValue -> unknown(
                id = id,
                eventContentDTO = eventContentDTO,
                cause = "Unknown value type for key: ${eventContentDTO.key} "
            )
        }
    }

    private fun deleteUserProperties(
        id: String,
        eventContentDTO: EventContentDTO.UserProperty.PropertiesDeleteDTO,
    ): Event {
        return when (eventContentDTO.key) {
            WIRE_RECEIPT_MODE.key -> ReadReceiptModeSet(id, false)
            WIRE_TYPING_INDICATOR_MODE.key -> TypingIndicatorModeSet(id, true)
            else -> unknown(
                id = id,
                eventContentDTO = eventContentDTO,
                cause = "Unknown key: ${eventContentDTO.key} "
            )
        }
    }

    private fun welcomeMessage(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MLSWelcomeDTO,
    ) = Event.Conversation.MLSWelcome(
        id,
        eventContentDTO.qualifiedConversation.toModel(),

        eventContentDTO.qualifiedFrom.toModel(),
        eventContentDTO.message,
    )

    private fun newMessage(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.NewMessageDTO,
    ) = Event.Conversation.NewMessage(
        id,
        eventContentDTO.qualifiedConversation.toModel(),
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
    ) = Event.Conversation.NewMLSMessage(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        subconversationId = eventContentDTO.subconversation?.let { SubconversationId(it) },
        senderUserId = eventContentDTO.qualifiedFrom.toModel(),
        messageInstant = eventContentDTO.time,
        content = eventContentDTO.message
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
    ) = Event.Conversation.NewConversation(
        id,
        eventContentDTO.qualifiedConversation.toModel(),
        eventContentDTO.qualifiedFrom.toModel(),
        eventContentDTO.time,
        eventContentDTO.data
    )

    fun conversationMemberJoin(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberJoinDTO,
    ) = Event.Conversation.MemberJoin(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        addedBy = eventContentDTO.qualifiedFrom.toModel(),
        members = eventContentDTO.members.users.map { memberMapper.fromApiModel(it) },
        dateTime = eventContentDTO.time,
    )

    fun conversationMemberLeave(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberLeaveDTO,
    ) = Event.Conversation.MemberLeave(
        id = id,
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        removedBy = eventContentDTO.qualifiedFrom.toModel(),
        removedList = eventContentDTO.removedUsers.qualifiedUserIds.map { it.toModel() },
        dateTime = eventContentDTO.time,
        reason = eventContentDTO.removedUsers.reason.toModel()
    )

    private fun memberUpdate(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberUpdateDTO,
    ): Event.Conversation.MemberChanged {
        return when {
            eventContentDTO.roleChange.role?.isNotEmpty() == true -> {
                Event.Conversation.MemberChanged.MemberChangedRole(
                    id = id,
                    conversationId = eventContentDTO.qualifiedConversation.toModel(),
                    timestampIso = eventContentDTO.time,
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
                    mutedConversationStatus = mapConversationMutedStatus(eventContentDTO.roleChange.mutedStatus)
                )
            }

            eventContentDTO.roleChange.isArchiving != null -> {
                Event.Conversation.MemberChanged.MemberArchivedStatusChanged(
                    id = id,
                    conversationId = eventContentDTO.qualifiedConversation.toModel(),
                    timestampIso = eventContentDTO.time,
                    archivedConversationChangedTime = eventContentDTO.roleChange.archivedRef.orEmpty(),
                    isArchiving = eventContentDTO.roleChange.isArchiving ?: false
                )
            }

            else -> {
                Event.Conversation.MemberChanged.IgnoredMemberChanged(
                    id,
                    eventContentDTO.qualifiedConversation.toModel(),
                )
            }
        }
    }

    @Suppress("MagicNumber")
    private fun mapConversationMutedStatus(status: Int?): MutedConversationStatus = when (status) {
        0 -> MutedConversationStatus.AllAllowed
        1 -> MutedConversationStatus.OnlyMentionsAndRepliesAllowed
        3 -> MutedConversationStatus.AllMuted
        else -> MutedConversationStatus.AllAllowed
    }

    @Suppress("LongMethod")
    private fun featureConfig(
        id: String,
        featureConfigUpdatedDTO: EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO,
    ) = when (featureConfigUpdatedDTO.data) {
        is FeatureConfigData.FileSharing -> Event.FeatureConfig.FileSharingUpdated(
            id,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.FileSharing)
        )

        is FeatureConfigData.SelfDeletingMessages -> Event.FeatureConfig.SelfDeletingMessagesConfig(
            id,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.SelfDeletingMessages)
        )

        is FeatureConfigData.MLS -> Event.FeatureConfig.MLSUpdated(
            id,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.MLS)
        )

        is FeatureConfigData.MLSMigration -> Event.FeatureConfig.MLSMigrationUpdated(
            id,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.MLSMigration)
        )

        is FeatureConfigData.ClassifiedDomains -> Event.FeatureConfig.ClassifiedDomainsUpdated(
            id,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.ClassifiedDomains)
        )

        is FeatureConfigData.ConferenceCalling -> Event.FeatureConfig.ConferenceCallingUpdated(
            id,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.ConferenceCalling)
        )

        is FeatureConfigData.ConversationGuestLinks -> Event.FeatureConfig.GuestRoomLinkUpdated(
            id,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.ConversationGuestLinks)
        )

        is FeatureConfigData.E2EI -> Event.FeatureConfig.MLSE2EIUpdated(
            id,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.E2EI)
        )

        is FeatureConfigData.AppLock -> Event.FeatureConfig.AppLockUpdated(
            id,
            featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.AppLock)
        )

        // These features are NOT received through events. As FeatureConfig Events are deprecated
        is FeatureConfigData.Channels,
        is FeatureConfigData.DigitalSignatures,
        is FeatureConfigData.Legalhold,
        is FeatureConfigData.SSO,
        is FeatureConfigData.SearchVisibility,
        is FeatureConfigData.SecondFactorPasswordChallenge,
        is FeatureConfigData.Unknown,
        is FeatureConfigData.ValidateSAMLEmails -> Event.FeatureConfig.UnknownFeatureUpdated(id)
    }

    private fun conversationDeleted(
        id: String,
        deletedConversationDTO: EventContentDTO.Conversation.DeletedConversationDTO,
    ) = Event.Conversation.DeletedConversation(
        id = id,
        conversationId = deletedConversationDTO.qualifiedConversation.toModel(),
        senderUserId = deletedConversationDTO.qualifiedFrom.toModel(),
        timestampIso = deletedConversationDTO.time
    )

    fun conversationRenamed(
        id: String,
        event: EventContentDTO.Conversation.ConversationRenameDTO,
    ) = Event.Conversation.RenamedConversation(
        id = id,
        conversationId = event.qualifiedConversation.toModel(),
        senderUserId = event.qualifiedFrom.toModel(),
        conversationName = event.updateNameData.conversationName,
        dateTime = event.time,
    )

    private fun teamMemberLeft(
        id: String,
        event: EventContentDTO.Team.MemberLeave,
    ) = Event.Team.MemberLeave(
        id = id,
        teamId = event.teamId,
        memberId = event.teamMember.nonQualifiedUserId,
        dateTime = event.time
    )

    private fun userUpdate(
        id: String,
        event: EventContentDTO.User.UpdateDTO,
    ) = Event.User.Update(
        id = id,
        userId = UserId(event.userData.nonQualifiedUserId, selfUserId.domain),
        accentId = event.userData.accentId,
        ssoIdDeleted = event.userData.ssoIdDeleted,
        name = event.userData.name,
        handle = event.userData.handle,
        email = event.userData.email,
        previewAssetId = event.userData.assets?.getPreviewAssetOrNull()?.key,
        completeAssetId = event.userData.assets?.getCompleteAssetOrNull()?.key,
        supportedProtocols = event.userData.supportedProtocols?.toModel()
    )

}

private fun MemberLeaveReasonDTO.toModel(): MemberLeaveReason = when (this) {
    MemberLeaveReasonDTO.LEFT -> MemberLeaveReason.Left
    MemberLeaveReasonDTO.REMOVED -> MemberLeaveReason.Removed
    MemberLeaveReasonDTO.USER_DELETED -> MemberLeaveReason.UserDeleted
}
