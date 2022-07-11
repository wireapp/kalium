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
                        (appLock.config.enforceAppLock, appLock.config.inactivityTimeoutSecs), appLock.status.name
                ),
                classifiedDomainsModel = ClassifiedDomainsModel(
                    ClassifiedDomainsConfigModel(classifiedDomains.config.domains), classifiedDomains.status.name
                ),
                conferenceCallingModel = ConfigsStatusModel(conferenceCalling.status.name),
                conversationGuestLinksModel = ConfigsStatusModel(conversationGuestLinks.status.name),
                digitalSignaturesModel = ConfigsStatusModel(digitalSignatures.status.name),
                fileSharingModel = ConfigsStatusModel(fileSharing.status.name),
                legalHoldModel = ConfigsStatusModel(legalHold.status.name),
                searchVisibilityModel = ConfigsStatusModel(searchVisibility.status.name),
                selfDeletingMessagesModel = SelfDeletingMessagesModel(
                    SelfDeletingMessagesConfigModel(selfDeletingMessages.config.enforcedTimeoutSeconds),
                    selfDeletingMessages.status.name
                ),
                sndFactorPasswordChallengeModel = ConfigsStatusModel(
                    sndFactorPasswordChallenge.status.name
                ),
                ssoModel = ConfigsStatusModel(sso.status.name),
                validateSAMLEmailsModel = ConfigsStatusModel(validateSAMLEmails.status.name)
            )
        }
}
