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
    val conferenceCalling: ConfigsStatus,
    @SerialName("conversationGuestLinks")
    val conversationGuestLinks: ConfigsStatus,
    @SerialName("digitalSignatures")
    val digitalSignatures: ConfigsStatus,
    @SerialName("fileSharing")
    val fileSharing: ConfigsStatus,
    @SerialName("legalhold")
    val legalHold: ConfigsStatus,
    @SerialName("searchVisibility")
    val searchVisibility: ConfigsStatus,
    @SerialName("selfDeletingMessages")
    val selfDeletingMessages: SelfDeletingMessages,
    @SerialName("sndFactorPasswordChallenge")
    val sndFactorPasswordChallenge: ConfigsStatus,
    @SerialName("sso")
    val sso: ConfigsStatus,
    @SerialName("validateSAMLemails")
    val validateSAMLEmails: ConfigsStatus
)


@Serializable
data class AppLock(
    @SerialName("config")
    val config: AppLockConfig,
    @SerialName("lockStatus")
    val lockStatus: String,
    @SerialName("status")
    val status: String
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
    @SerialName("lockStatus")
    val lockStatus: String,
    @SerialName("status")
    val status: String
)


@Serializable
data class ClassifiedDomainsConfig(
    @SerialName("domains")
    val domains: List<String>
)


@Serializable
data class ConfigsStatus(
    @SerialName("lockStatus")
    val lockStatus: String,
    @SerialName("status")
    val status: String
)

@Serializable
data class SelfDeletingMessages(
    @SerialName("config")
    val config: SelfDeletingMessagesConfig,
    @SerialName("lockStatus")
    val lockStatus: String,
    @SerialName("status")
    val status: String
)

@Serializable
data class SelfDeletingMessagesConfig(
    @SerialName("enforcedTimeoutSeconds")
    val enforcedTimeoutSeconds: Int
)
