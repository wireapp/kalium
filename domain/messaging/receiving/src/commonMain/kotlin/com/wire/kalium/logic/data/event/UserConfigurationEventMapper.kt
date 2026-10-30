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

import com.wire.kalium.logic.data.conversation.folders.toFolder
import com.wire.kalium.logic.data.event.Event.UserProperty.ReadReceiptModeSet
import com.wire.kalium.logic.data.event.Event.UserProperty.TypingIndicatorModeSet
import com.wire.kalium.logic.data.featureConfig.FeatureConfigMapper
import com.wire.kalium.logic.data.featureConfig.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.toModel
import com.wire.kalium.network.api.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.properties.PropertyKey.WIRE_RECEIPT_MODE
import com.wire.kalium.network.api.authenticated.properties.PropertyKey.WIRE_TYPING_INDICATOR_MODE
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer

fun updateUserProperties(
    selfUserId: UserId,
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

fun deleteUserProperties(
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

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun featureConfig(
    featureConfigMapper: FeatureConfigMapper,
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

    is FeatureConfigData.AllowedGlobalOperations -> Event.FeatureConfig.AllowedGlobalOperationsUpdated(
        id,
        featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.AllowedGlobalOperations)
    )

    is FeatureConfigData.Cells -> Event.FeatureConfig.CellsConfigUpdated(
        id,
        featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.Cells)
    )

    is FeatureConfigData.CellsInternal -> Event.FeatureConfig.CellsInternalConfigUpdated(
        id,
        featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.CellsInternal)
    )
    is FeatureConfigData.EnableUserProfileQRCode -> Event.FeatureConfig.EnableUserProfileQRCodeConfigUpdated(
        id,
        featureConfigMapper.fromDTO(featureConfigUpdatedDTO.data as FeatureConfigData.EnableUserProfileQRCode)
    )

    is FeatureConfigData.AssetAuditLog -> Event.FeatureConfig.AssetAuditLogConfigUpdated(
        id,
        (featureConfigUpdatedDTO.data as FeatureConfigData.AssetAuditLog).toModel()
    )

    is FeatureConfigData.PreventAdminlessGroups -> Event.FeatureConfig.PreventAdminlessGroupsConfigUpdated(
        id,
        (featureConfigUpdatedDTO.data as FeatureConfigData.PreventAdminlessGroups).toModel()
    )

    is FeatureConfigData.Meetings -> Event.FeatureConfig.MeetingsConfigUpdated(
        id,
        (featureConfigUpdatedDTO.data as FeatureConfigData.Meetings).toModel()
    )

    // These features are NOT received through events. As FeatureConfig Events are deprecated
    is FeatureConfigData.ConsumableNotifications,
    is FeatureConfigData.Apps,
    is FeatureConfigData.Channels,
    is FeatureConfigData.DigitalSignatures,
    is FeatureConfigData.Legalhold,
    is FeatureConfigData.SSO,
    is FeatureConfigData.SearchVisibility,
    is FeatureConfigData.SecondFactorPasswordChallenge,
    is FeatureConfigData.Unknown,
    is FeatureConfigData.ValidateSAMLEmails -> Event.FeatureConfig.UnknownFeatureUpdated(id)
}

fun userUpdate(
    selfUserId: UserId,
    id: String,
    event: EventContentDTO.User.UpdateDTO,
) = Event.User.Update(
    id = id,
    userId = UserId(event.userData.nonQualifiedUserId, selfUserId.domain),
    accentId = event.userData.accentId,
    ssoIdDeleted = event.userData.ssoIdDeleted,
    name = event.userData.name,
    textStatus = event.userData.textStatus,
    handle = event.userData.handle,
    email = event.userData.email,
    previewAssetId = event.userData.assets?.getPreviewAssetOrNull()?.key,
    completeAssetId = event.userData.assets?.getCompleteAssetOrNull()?.key,
    supportedProtocols = event.userData.supportedProtocols?.toModel()
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
