package com.wire.kalium.network.api.featureConfigs


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeatureConfigResponse(
    @SerialName("appLock")
    val appLock: AppLock,
    @SerialName("classifiedDomains")
    val classifiedDomains: ClassifiedDomains,
    @SerialName("conferenceCalling")
    val conferenceCalling: ConfigsStatusDTO,
    @SerialName("conversationGuestLinks")
    val conversationGuestLinks: ConfigsStatusDTO,
    @SerialName("digitalSignatures")
    val digitalSignatures: ConfigsStatusDTO,
    @SerialName("fileSharing")
    val fileSharing: ConfigsStatusDTO,
    @SerialName("legalhold")
    val legalHold: ConfigsStatusDTO,
    @SerialName("searchVisibility")
    val searchVisibility: ConfigsStatusDTO,
    @SerialName("selfDeletingMessages")
    val selfDeletingMessages: SelfDeletingMessages,
    @SerialName("sndFactorPasswordChallenge")
    val sndFactorPasswordChallenge: ConfigsStatusDTO,
    @SerialName("sso")
    val sso: ConfigsStatusDTO,
    @SerialName("validateSAMLemails")
    val validateSAMLEmails: ConfigsStatusDTO
)


@Serializable
data class AppLock(
    @SerialName("config")
    val config: AppLockConfig,
    @SerialName("status")
    val status: FeatureFlagStatusDTO
)


@Serializable
data class AppLockConfig(
    @SerialName("enforceAppLock")
    val enforceAppLock: Boolean,
    @SerialName("inactivityTimeoutSecs")
    val inactivityTimeoutSecs: Int
)


@Serializable
data class ClassifiedDomains(
    @SerialName("config")
    val config: ClassifiedDomainsConfig,
    @SerialName("status")
    val status: FeatureFlagStatusDTO
)


@Serializable
data class ClassifiedDomainsConfig(
    @SerialName("domains")
    val domains: List<String>
)

@Serializable
data class ConfigsStatusDTO(
    @SerialName("status")
    val status: FeatureFlagStatusDTO
)

@Serializable
enum class FeatureFlagStatusDTO {
    @SerialName("enabled")
    ENABLED,
    @SerialName("disabled")
    DISABLED;
}

@Serializable
data class SelfDeletingMessages(
    @SerialName("config")
    val config: SelfDeletingMessagesConfig,
    @SerialName("status")
    val status: FeatureFlagStatusDTO
)

@Serializable
data class SelfDeletingMessagesConfig(
    @SerialName("enforcedTimeoutSeconds")
    val enforcedTimeoutSeconds: Int
)
