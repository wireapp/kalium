package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.network.api.featureConfigs.FeatureConfigResponse
import com.wire.kalium.network.api.featureConfigs.FeatureFlagStatusDTO

interface FeatureConfigMapper {
    fun fromFeatureConfigsDTO(featureConfigResponse: FeatureConfigResponse): FeatureConfigModel
    fun fromFeatureConfigsDTO(status: FeatureFlagStatusDTO): Status
}

class FeatureConfigMapperImpl : FeatureConfigMapper {
    override fun fromFeatureConfigsDTO(featureConfigResponse: FeatureConfigResponse): FeatureConfigModel =
        with(featureConfigResponse) {
            FeatureConfigModel(
                appLockModel = AppLockModel(
                    AppLockConfigModel(appLock.config.enforceAppLock, appLock.config.inactivityTimeoutSecs),
                    fromFeatureConfigsDTO(appLock.status)
                ),
                classifiedDomainsModel = ClassifiedDomainsModel(
                    ClassifiedDomainsConfigModel(classifiedDomains.config.domains),
                    fromFeatureConfigsDTO(classifiedDomains.status)
                ),
                conferenceCallingModel = ConfigsStatusModel(fromFeatureConfigsDTO(conferenceCalling.status)),
                conversationGuestLinksModel = ConfigsStatusModel(fromFeatureConfigsDTO(conversationGuestLinks.status)),
                digitalSignaturesModel = ConfigsStatusModel(fromFeatureConfigsDTO(digitalSignatures.status)),
                fileSharingModel = ConfigsStatusModel(fromFeatureConfigsDTO(fileSharing.status)),
                legalHoldModel = ConfigsStatusModel(fromFeatureConfigsDTO(legalHold.status)),
                searchVisibilityModel = ConfigsStatusModel(fromFeatureConfigsDTO(searchVisibility.status)),
                selfDeletingMessagesModel = SelfDeletingMessagesModel(
                    SelfDeletingMessagesConfigModel(selfDeletingMessages.config.enforcedTimeoutSeconds),
                    fromFeatureConfigsDTO(selfDeletingMessages.status)
                ),
                sndFactorPasswordChallengeModel = ConfigsStatusModel(
                    fromFeatureConfigsDTO(sndFactorPasswordChallenge.status)
                ),
                ssoModel = ConfigsStatusModel(fromFeatureConfigsDTO(sso.status)),
                validateSAMLEmailsModel = ConfigsStatusModel(fromFeatureConfigsDTO(validateSAMLEmails.status))
            )
        }

    override fun fromFeatureConfigsDTO(status: FeatureFlagStatusDTO): Status =
        when (status) {
            FeatureFlagStatusDTO.ENABLED -> Status.ENABLED
            FeatureFlagStatusDTO.DISABLED -> Status.DISABLED
        }
}
