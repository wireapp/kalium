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
                        (appLock.config.enforceAppLock, appLock.config.inactivityTimeoutSecs),
                    appLock.lockStatus, appLock.status
                ),
                classifiedDomainsModel = ClassifiedDomainsModel(
                    ClassifiedDomainsConfigModel(classifiedDomains.config.domains),
                    classifiedDomains.lockStatus, classifiedDomains.status
                ),
                conferenceCallingModel = ConfigsStatusModel(conferenceCalling.lockStatus, conferenceCalling.status),
                conversationGuestLinksModel = ConfigsStatusModel(conversationGuestLinks.lockStatus, conversationGuestLinks.status),
                digitalSignaturesModel = ConfigsStatusModel(digitalSignatures.lockStatus, digitalSignatures.status),
                fileSharingModel = ConfigsStatusModel(fileSharing.lockStatus, fileSharing.status),
                legalHoldModel = ConfigsStatusModel(legalHold.lockStatus, legalHold.status),
                searchVisibilityModel = ConfigsStatusModel(searchVisibility.lockStatus, searchVisibility.status),
                selfDeletingMessagesModel = SelfDeletingMessagesModel(
                    SelfDeletingMessagesConfigModel(selfDeletingMessages.config.enforcedTimeoutSeconds),
                    selfDeletingMessages.lockStatus,
                    selfDeletingMessages.status
                ),
                sndFactorPasswordChallengeModel = ConfigsStatusModel(
                    sndFactorPasswordChallenge.lockStatus,
                    sndFactorPasswordChallenge.status
                ),
                ssoModel = ConfigsStatusModel(sso.lockStatus, sso.status),
                validateSAMLEmailsModel = ConfigsStatusModel(validateSAMLEmails.lockStatus, validateSAMLEmails.status)
            )
        }
}
