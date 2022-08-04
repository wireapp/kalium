package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.logic.data.id.PlainId

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
    val validateSAMLEmailsModel: ConfigsStatusModel,
    val mlsModel: MLSModel
)

enum class Status {
    ENABLED,
    DISABLED
}

data class AppLockModel(
    val config: AppLockConfigModel,
    val status: Status
)

data class AppLockConfigModel(
    val enforceAppLock: Boolean,
    val inactivityTimeoutSecs: Int
)

data class ClassifiedDomainsModel(
    val config: ClassifiedDomainsConfigModel,
    val status: Status
)

data class ClassifiedDomainsConfigModel(
    val domains: List<String>
)

data class ConfigsStatusModel(
    val status: Status
)

data class SelfDeletingMessagesModel(
    val config: SelfDeletingMessagesConfigModel,
    val status: Status
)

data class SelfDeletingMessagesConfigModel(
    val enforcedTimeoutSeconds: Int
)

data class MLSModel(
    val allowedUsers: List<PlainId>,
    val status: Status
)
