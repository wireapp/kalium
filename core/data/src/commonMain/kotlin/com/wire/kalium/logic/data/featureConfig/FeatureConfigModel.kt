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

package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.util.time.Second
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class FeatureConfigModel(
    val appLockModel: AppLockModel,
    val classifiedDomainsModel: ClassifiedDomainsModel,
    val conferenceCallingModel: ConferenceCallingModel,
    val conversationGuestLinksModel: ConfigsStatusModel,
    val digitalSignaturesModel: ConfigsStatusModel,
    val fileSharingModel: ConfigsStatusModel,
    val guestRoomLinkModel: ConfigsStatusModel,
    val legalHoldModel: ConfigsStatusModel,
    val searchVisibilityModel: ConfigsStatusModel,
    val selfDeletingMessagesModel: SelfDeletingMessagesModel,
    val secondFactorPasswordChallengeModel: ConfigsStatusModel,
    val ssoModel: ConfigsStatusModel,
    val validateSAMLEmailsModel: ConfigsStatusModel,
    val mlsModel: MLSModel,
    val e2EIModel: E2EIModel,
    val mlsMigrationModel: MLSMigrationModel?,
    val channelsModel: ChannelFeatureConfiguration,
    val consumableNotificationsModel: ConfigsStatusModel?,
    val allowedGlobalOperationsModel: AllowedGlobalOperationsModel?,
    val cellsModel: CellsModel?,
    val cellsInternalModel: CellsInternalModel?,
    val appsModel: ConfigsStatusModel?,
    val enableUserProfileQRCodeConfigModel: EnableUserProfileQRCodeConfigModel?,
    val assetAuditLogConfigModel: AssetAuditLogConfigModel?,
)

enum class Status {
    ENABLED,
    DISABLED;

    fun toBoolean(): Boolean = this == ENABLED
}

@Serializable
data class AppLockModel(
    @SerialName("config")
    val status: Status,
    @SerialName("inactivityTimeoutSecs")
    val inactivityTimeoutSecs: Second
)

@Serializable
data class ClassifiedDomainsModel(
    @SerialName("config")
    val config: ClassifiedDomainsConfigModel,
    @SerialName("status")
    val status: Status
)

@Serializable
data class ClassifiedDomainsConfigModel(
    @SerialName("domains")
    val domains: List<String>
)

@Serializable
data class ConfigsStatusModel(
    @SerialName("status")
    val status: Status
)

@Serializable
data class SelfDeletingMessagesModel(
    @SerialName("config")
    val config: SelfDeletingMessagesConfigModel,
    @SerialName("status")
    val status: Status
)

@Serializable
data class SelfDeletingMessagesConfigModel(
    @SerialName("enforcedTimeoutSeconds")
    val enforcedTimeoutSeconds: Long?
)

@Serializable
data class MLSModel(
    @SerialName("defaultProtocol")
    val defaultProtocol: SupportedProtocol,
    @SerialName("supportedProtocols")
    val supportedProtocols: Set<SupportedProtocol>,
    @SerialName("supportedCipherSuite")
    val supportedCipherSuite: SupportedCipherSuite?,
    @SerialName("status")
    val status: Status
)

@Serializable
data class MLSMigrationModel(
    @SerialName("startTime")
    val startTime: Instant?,
    @SerialName("endTime")
    val endTime: Instant?,
    @SerialName("status")
    val status: Status
)

@Serializable
data class ConferenceCallingModel(
    @SerialName("status")
    val status: Status,
    @SerialName("useSFTForOneOnOneCalls")
    val useSFTForOneOnOneCalls: Boolean
)

@Serializable
data class E2EIModel(
    @SerialName("config")
    val config: E2EIConfigModel,
    @SerialName("status")
    val status: Status
)

@Serializable
data class E2EIConfigModel(
    @SerialName("discoverUrl")
    val discoverUrl: String?,
    @SerialName("verificationExpirationSeconds")
    val verificationExpirationSeconds: Long,
    @SerialName("shouldUseProxy")
    val shouldUseProxy: Boolean,
    @SerialName("crlProxy")
    val crlProxy: String?,
)

@Serializable
data class AllowedGlobalOperationsModel(
    @SerialName("mlsConversationsReset")
    val mlsConversationsReset: Boolean,
    @SerialName("status")
    val status: Status,
)

@Serializable
data class EnableUserProfileQRCodeConfigModel(
    @SerialName("status")
    val status: Status,
)

@Serializable
data class AssetAuditLogConfigModel(
    @SerialName("status")
    val status: Status
)

@Serializable
sealed interface ChannelFeatureConfiguration {

    @Serializable
    data object Disabled : ChannelFeatureConfiguration

    @Serializable
    data class Enabled(
        /**
         * Says what user types the team allows to create any kind of channels.
         */
        val createChannelsRequirement: TeamUserType,

        /**
         * Says what user types the team allows to create public channels.
         */
        val createPublicChannelsRequirement: TeamUserType,
    ) : ChannelFeatureConfiguration

    @Serializable
    enum class TeamUserType {
        ADMINS_ONLY,

        /**
         * Team Admins and regular team members (non guests and non externals)
         */
        ADMINS_AND_REGULAR_MEMBERS,

        /**
         * Team Admins, regular team members, and externals
         */
        EVERYONE_IN_THE_TEAM
    }
}
