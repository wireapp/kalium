package com.wire.kalium.logic.data.featureConfig

data class FeatureConfigModel(
    val appLockModel: AppLockModel,
    val classifiedDomainsModel: ClassifiedDomainsModel,
    val conferenceCallingModel: ConfigsStatusModel,
    val conversationGuestLinksModel: ConfigsStatusModel,
    val digitalSignaturesModel: ConfigsStatusModel,
    val fileSharingModel: ConfigsStatusModel,
    val legalHoldModel: ConfigsStatusModel,
    val searchVisibilityModel: ConfigsStatusModel,
    val selfDeletingMessagesModel: SelfDeletingMessagesModel,
    val sndFactorPasswordChallengeModel: ConfigsStatusModel,
    val ssoModel: ConfigsStatusModel,
    val validateSAMLEmailsModel: ConfigsStatusModel
)

data class AppLockModel(
    val config: AppLockConfigModel,
    val lockStatus: String,
    val status: String
)

data class AppLockConfigModel(
    val enforceAppLock: Boolean,
    val inactivityTimeoutSecs: Int
)

data class ClassifiedDomainsModel(
    val config: ClassifiedDomainsConfigModel,
    val lockStatus: String,
    val status: String
)

data class ClassifiedDomainsConfigModel(
    val domains: List<String>
)

data class ConfigsStatusModel(
    val lockStatus: String,
    val status: String
)

data class SelfDeletingMessagesModel(
    val config: SelfDeletingMessagesConfigModel,
    val lockStatus: String,
    val status: String
)

data class SelfDeletingMessagesConfigModel(
    val enforcedTimeoutSeconds: Int
)
