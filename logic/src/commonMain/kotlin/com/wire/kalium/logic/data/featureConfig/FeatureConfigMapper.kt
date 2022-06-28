package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.network.api.featureConfigs.FeatureConfigResponse

interface FeatureConfigMapper {
    fun fromFeatureConfigsDTO(featureConfigResponse: FeatureConfigResponse): FeatureConfigModel
}

class FeatureConfigMapperImpl : FeatureConfigMapper {
    override fun fromFeatureConfigsDTO(featureConfigResponse: FeatureConfigResponse): FeatureConfigModel =
        with(featureConfigResponse) {
            FeatureConfigModel(
                appLockModel = AppLockModel(
                    AppLockConfigModel
                        (appLock.config.enforceAppLock, appLock.config.inactivityTimeoutSecs), appLock.status
                ),
                classifiedDomainsModel = ClassifiedDomainsModel(
                    ClassifiedDomainsConfigModel(classifiedDomains.config.domains), classifiedDomains.status
                ),
                conferenceCallingModel = ConfigsStatusModel(conferenceCalling.status),
                conversationGuestLinksModel = ConfigsStatusModel(conversationGuestLinks.status),
                digitalSignaturesModel = ConfigsStatusModel(digitalSignatures.status),
                fileSharingModel = ConfigsStatusModel(fileSharing.status),
                legalHoldModel = ConfigsStatusModel(legalHold.status),
                searchVisibilityModel = ConfigsStatusModel(searchVisibility.status),
                selfDeletingMessagesModel = SelfDeletingMessagesModel(
                    SelfDeletingMessagesConfigModel(selfDeletingMessages.config.enforcedTimeoutSeconds),
                    selfDeletingMessages.status
                ),
                sndFactorPasswordChallengeModel = ConfigsStatusModel(
                    sndFactorPasswordChallenge.status
                ),
                ssoModel = ConfigsStatusModel(sso.status),
                validateSAMLEmailsModel = ConfigsStatusModel(validateSAMLEmails.status)
            )
        }
}
