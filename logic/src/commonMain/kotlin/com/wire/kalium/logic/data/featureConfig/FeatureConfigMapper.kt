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

package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigResponse
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureFlagStatusDTO

interface FeatureConfigMapper {
    fun fromDTO(featureConfigResponse: FeatureConfigResponse): FeatureConfigModel
    fun fromDTO(status: FeatureFlagStatusDTO): Status
    fun fromDTO(data: FeatureConfigData.MLS?): MLSModel
    fun fromDTO(data: FeatureConfigData.AppLock): AppLockModel
    fun fromDTO(data: FeatureConfigData.ClassifiedDomains): ClassifiedDomainsModel
    fun fromDTO(data: FeatureConfigData.SelfDeletingMessages): SelfDeletingMessagesModel
    fun fromDTO(data: FeatureConfigData.FileSharing): ConfigsStatusModel
    fun fromDTO(data: FeatureConfigData.ConferenceCalling): ConferenceCallingModel
}

class FeatureConfigMapperImpl : FeatureConfigMapper {
    override fun fromDTO(featureConfigResponse: FeatureConfigResponse): FeatureConfigModel =
        with(featureConfigResponse) {
            FeatureConfigModel(
                appLockModel = fromDTO(featureConfigResponse.appLock),
                classifiedDomainsModel = fromDTO(featureConfigResponse.classifiedDomains),
                conferenceCallingModel = fromDTO(conferenceCalling),
                conversationGuestLinksModel = ConfigsStatusModel(fromDTO(conversationGuestLinks.status)),
                digitalSignaturesModel = ConfigsStatusModel(fromDTO(digitalSignatures.status)),
                fileSharingModel = fromDTO(fileSharing),
                legalHoldModel = ConfigsStatusModel(fromDTO(legalHold.status)),
                searchVisibilityModel = ConfigsStatusModel(fromDTO(searchVisibility.status)),
                selfDeletingMessagesModel = fromDTO(featureConfigResponse.selfDeletingMessages),
                sndFactorPasswordChallengeModel = ConfigsStatusModel(
                    fromDTO(sndFactorPasswordChallenge.status)
                ),
                ssoModel = ConfigsStatusModel(fromDTO(sso.status)),
                validateSAMLEmailsModel = ConfigsStatusModel(fromDTO(validateSAMLEmails.status)),
                mlsModel = fromDTO(mls)
            )
        }

    override fun fromDTO(status: FeatureFlagStatusDTO): Status =
        when (status) {
            FeatureFlagStatusDTO.ENABLED -> Status.ENABLED
            FeatureFlagStatusDTO.DISABLED -> Status.DISABLED
        }

    override fun fromDTO(data: FeatureConfigData.MLS?): MLSModel =
        data?.let {
            MLSModel(
                it.config.protocolToggleUsers.map { userId -> PlainId(userId) },
                fromDTO(it.status)
            )
        } ?: MLSModel(
            listOf(),
            Status.DISABLED
        )

    override fun fromDTO(data: FeatureConfigData.AppLock): AppLockModel =
        AppLockModel(
            AppLockConfigModel(data.config.enforceAppLock, data.config.inactivityTimeoutSecs),
            fromDTO(data.status)
        )

    override fun fromDTO(data: FeatureConfigData.ClassifiedDomains): ClassifiedDomainsModel =
        ClassifiedDomainsModel(
            ClassifiedDomainsConfigModel(data.config.domains),
            fromDTO(data.status)
        )

    override fun fromDTO(data: FeatureConfigData.SelfDeletingMessages): SelfDeletingMessagesModel =
        SelfDeletingMessagesModel(
            SelfDeletingMessagesConfigModel(data.config.enforcedTimeoutSeconds),
            fromDTO(data.status)
        )

    override fun fromDTO(data: FeatureConfigData.FileSharing): ConfigsStatusModel =
        ConfigsStatusModel(
            fromDTO(data.status)
        )

    override fun fromDTO(data: FeatureConfigData.ConferenceCalling): ConferenceCallingModel =
        ConferenceCallingModel(
            status = fromDTO(data.status)
        )
}
