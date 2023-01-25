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

package com.wire.kalium.network.api.base.authenticated.featureConfigs

import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
data class FeatureConfigResponse(
    @SerialName("appLock")
    val appLock: FeatureConfigData.AppLock,
    @SerialName("classifiedDomains")
    val classifiedDomains: FeatureConfigData.ClassifiedDomains,
    @SerialName("conferenceCalling")
    val conferenceCalling: FeatureConfigData.ConferenceCalling,
    @SerialName("conversationGuestLinks")
    val conversationGuestLinks: FeatureConfigData.ConversationGuestLinks,
    @SerialName("digitalSignatures")
    val digitalSignatures: FeatureConfigData.DigitalSignatures,
    @SerialName("fileSharing")
    val fileSharing: FeatureConfigData.FileSharing,
    @SerialName("legalhold")
    val legalHold: FeatureConfigData.Legalhold,
    @SerialName("searchVisibility")
    val searchVisibility: FeatureConfigData.SearchVisibility,
    @SerialName("selfDeletingMessages")
    val selfDeletingMessages: FeatureConfigData.SelfDeletingMessages,
    @SerialName("sndFactorPasswordChallenge")
    val sndFactorPasswordChallenge: FeatureConfigData.SecondFactorPasswordChallenge,
    @SerialName("sso")
    val sso: FeatureConfigData.SSO,
    @SerialName("validateSAMLemails")
    val validateSAMLEmails: FeatureConfigData.ValidateSAMLEmails,
    @SerialName("mls")
    val mls: FeatureConfigData.MLS?
)

@Serializable
enum class FeatureFlagStatusDTO {
    @SerialName("enabled")
    ENABLED,
    @SerialName("disabled")
    DISABLED;
}

@Serializable
data class AppLockConfigDTO(
    @SerialName("enforceAppLock")
    val enforceAppLock: Boolean,
    @SerialName("inactivityTimeoutSecs")
    val inactivityTimeoutSecs: Int
)

@Serializable
data class ClassifiedDomainsConfigDTO(
    @SerialName("domains")
    val domains: List<String>
)
@Serializable
data class MLSConfigDTO(
    @SerialName("protocolToggleUsers")
    val protocolToggleUsers: List<String>,
    @SerialName("defaultProtocol")
    val defaultProtocol: ConvProtocol,
    @SerialName("allowedCipherSuites")
    val allowedCipherSuites: List<Int>,
    @SerialName("defaultCipherSuite")
    val defaultCipherSuite: Int
)

@Serializable
data class SelfDeletingMessagesConfigDTO(
    @SerialName("enforcedTimeoutSeconds")
    val enforcedTimeoutSeconds: Int
)

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("name")
@Serializable
sealed class FeatureConfigData {

    @Serializable
    data class Unknown(
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("appLock")
    @Serializable
    data class AppLock(
        @SerialName("config")
        val config: AppLockConfigDTO,
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("classifiedDomains")
    @Serializable
    data class ClassifiedDomains(
        @SerialName("config")
        val config: ClassifiedDomainsConfigDTO,
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("conferenceCalling")
    @Serializable
    data class ConferenceCalling(
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("conversationGuestLinks")
    @Serializable
    data class ConversationGuestLinks(
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("digitalSignatures")
    @Serializable
    data class DigitalSignatures(
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("fileSharing")
    @Serializable
    data class FileSharing(
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("legalhold")
    @Serializable
    data class Legalhold(
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("searchVisibility")
    @Serializable
    data class SearchVisibility(
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("selfDeletingMessages")
    @Serializable
    data class SelfDeletingMessages(
        @SerialName("config")
        val config: SelfDeletingMessagesConfigDTO,
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("sndFactorPasswordChallenge")
    @Serializable
    data class SecondFactorPasswordChallenge(
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("sso")
    @Serializable
    data class SSO(
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("validateSAMLemails")
    @Serializable
    data class ValidateSAMLEmails(
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()

    @SerialName("mls")
    @Serializable
    data class MLS(
        @SerialName("config")
        val config: MLSConfigDTO,
        @SerialName("status")
        val status: FeatureFlagStatusDTO
    ) : FeatureConfigData()
}
