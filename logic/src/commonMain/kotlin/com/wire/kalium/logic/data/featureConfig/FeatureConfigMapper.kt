package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.network.api.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.featureConfigs.FeatureConfigResponse
import com.wire.kalium.network.api.featureConfigs.FeatureFlagStatusDTO

interface FeatureConfigMapper {
    fun fromFeatureConfigsDTO(featureConfigResponse: FeatureConfigResponse): FeatureConfigModel
    fun fromFeatureConfigsDTO(status: FeatureFlagStatusDTO): Status
    fun fromFeatureConfigsDTO(data: FeatureConfigData.MLS): MLSModel
    fun fromFeatureConfigsDTO(data: FeatureConfigData.AppLock): AppLockModel
    fun fromFeatureConfigsDTO(data: FeatureConfigData.ClassifiedDomains): ClassifiedDomainsModel
    fun fromFeatureConfigsDTO(data: FeatureConfigData.SelfDeletingMessages): SelfDeletingMessagesModel
    fun fromFeatureConfigsDTO(data: FeatureConfigData.FileSharing): ConfigsStatusModel
}

class FeatureConfigMapperImpl : FeatureConfigMapper {
    override fun fromFeatureConfigsDTO(featureConfigResponse: FeatureConfigResponse): FeatureConfigModel =
        with(featureConfigResponse) {
            FeatureConfigModel(
                appLockModel = fromFeatureConfigsDTO(featureConfigResponse.appLock),
                classifiedDomainsModel = fromFeatureConfigsDTO(featureConfigResponse.classifiedDomains),
                conferenceCallingModel = ConfigsStatusModel(fromFeatureConfigsDTO(conferenceCalling.status)),
                conversationGuestLinksModel = ConfigsStatusModel(fromFeatureConfigsDTO(conversationGuestLinks.status)),
                digitalSignaturesModel = ConfigsStatusModel(fromFeatureConfigsDTO(digitalSignatures.status)),
                fileSharingModel = fromFeatureConfigsDTO(fileSharing),
                legalHoldModel = ConfigsStatusModel(fromFeatureConfigsDTO(legalHold.status)),
                searchVisibilityModel = ConfigsStatusModel(fromFeatureConfigsDTO(searchVisibility.status)),
                selfDeletingMessagesModel = fromFeatureConfigsDTO(featureConfigResponse.selfDeletingMessages),
                sndFactorPasswordChallengeModel = ConfigsStatusModel(
                    fromFeatureConfigsDTO(sndFactorPasswordChallenge.status)
                ),
                ssoModel = ConfigsStatusModel(fromFeatureConfigsDTO(sso.status)),
                validateSAMLEmailsModel = ConfigsStatusModel(fromFeatureConfigsDTO(validateSAMLEmails.status)),
                mlsModel = fromFeatureConfigsDTO(mls)
            )
        }

    override fun fromFeatureConfigsDTO(status: FeatureFlagStatusDTO): Status =
        when (status) {
            FeatureFlagStatusDTO.ENABLED -> Status.ENABLED
            FeatureFlagStatusDTO.DISABLED -> Status.DISABLED
        }

    override fun fromFeatureConfigsDTO(data: FeatureConfigData.MLS): MLSModel =
        MLSModel(
            fromFeatureConfigsDTO(data.status),
            data.config.protocolToggleUsers.map { PlainId(it) }
        )

    override fun fromFeatureConfigsDTO(data: FeatureConfigData.AppLock): AppLockModel =
        AppLockModel(
            AppLockConfigModel(data.config.enforceAppLock, data.config.inactivityTimeoutSecs),
            fromFeatureConfigsDTO(data.status)
        )

    override fun fromFeatureConfigsDTO(data: FeatureConfigData.ClassifiedDomains): ClassifiedDomainsModel =
        ClassifiedDomainsModel(
            ClassifiedDomainsConfigModel(data.config.domains),
            fromFeatureConfigsDTO(data.status)
        )

    override fun fromFeatureConfigsDTO(data: FeatureConfigData.SelfDeletingMessages): SelfDeletingMessagesModel =
        SelfDeletingMessagesModel(
            SelfDeletingMessagesConfigModel(data.config.enforcedTimeoutSeconds),
            fromFeatureConfigsDTO(data.status)
        )

    override fun fromFeatureConfigsDTO(data: FeatureConfigData.FileSharing): ConfigsStatusModel =
        ConfigsStatusModel(
            fromFeatureConfigsDTO(data.status)
        )
}
