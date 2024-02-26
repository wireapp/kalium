/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.toModel
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigResponse
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.MLSMigrationConfigDTO
import com.wire.kalium.persistence.config.MLSMigrationEntity

interface FeatureConfigMapper {
    fun fromDTO(featureConfigResponse: FeatureConfigResponse): FeatureConfigModel
    fun fromDTO(status: FeatureFlagStatusDTO): Status
    fun fromDTO(data: FeatureConfigData.MLS?): MLSModel
    fun fromDTO(data: FeatureConfigData.MLSMigration): MLSMigrationModel
    fun fromDTO(data: FeatureConfigData.AppLock): AppLockModel
    fun fromDTO(data: FeatureConfigData.ClassifiedDomains): ClassifiedDomainsModel
    fun fromDTO(data: FeatureConfigData.SelfDeletingMessages): SelfDeletingMessagesModel
    fun fromDTO(data: FeatureConfigData.FileSharing): ConfigsStatusModel
    fun fromDTO(data: FeatureConfigData.ConferenceCalling): ConferenceCallingModel
    fun fromDTO(data: FeatureConfigData.ConversationGuestLinks): ConfigsStatusModel
    fun fromDTO(data: FeatureConfigData.E2EI?): E2EIModel
    fun fromModel(status: Status): FeatureFlagStatusDTO
    fun fromModel(model: MLSMigrationModel): FeatureConfigData.MLSMigration
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
                guestRoomLinkModel = fromDTO(conversationGuestLinks),
                legalHoldModel = ConfigsStatusModel(fromDTO(legalHold.status)),
                searchVisibilityModel = ConfigsStatusModel(fromDTO(searchVisibility.status)),
                selfDeletingMessagesModel = fromDTO(featureConfigResponse.selfDeletingMessages),
                secondFactorPasswordChallengeModel = ConfigsStatusModel(
                    fromDTO(sndFactorPasswordChallenge.status)
                ),
                ssoModel = ConfigsStatusModel(fromDTO(sso.status)),
                validateSAMLEmailsModel = ConfigsStatusModel(fromDTO(validateSAMLEmails.status)),
                mlsModel = fromDTO(mls),
                e2EIModel = fromDTO(mlsE2EI),
                mlsMigrationModel = mlsMigration?.let { fromDTO(it) }
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
                it.config.defaultProtocol.toModel(),
                it.config.supportedProtocols.map { it.toModel() }.toSet(),
                fromDTO(it.status)
            )
        } ?: MLSModel(
            listOf(),
            SupportedProtocol.PROTEUS,
            setOf(SupportedProtocol.PROTEUS),
            Status.DISABLED
        )

    @Suppress("MagicNumber")
    override fun fromDTO(data: FeatureConfigData.MLSMigration): MLSMigrationModel =
        MLSMigrationModel(
            data.config.startTime,
            data.config.finaliseRegardlessAfter,
            fromDTO(data.status)
        )

    override fun fromDTO(data: FeatureConfigData.AppLock): AppLockModel =
        AppLockModel(
            status = if (data.config.enforceAppLock) Status.ENABLED else Status.DISABLED,
            inactivityTimeoutSecs = data.config.inactivityTimeoutSecs
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

    override fun fromDTO(data: FeatureConfigData.ConversationGuestLinks): ConfigsStatusModel =
        ConfigsStatusModel(
            fromDTO(data.status)
        )

    override fun fromDTO(data: FeatureConfigData.ConferenceCalling): ConferenceCallingModel =
        ConferenceCallingModel(
            status = fromDTO(data.status)
        )

    override fun fromDTO(data: FeatureConfigData.E2EI?): E2EIModel =
        data?.let {
            E2EIModel(
                E2EIConfigModel(
                    data.config.url,
                    data.config.verificationExpirationSeconds
                ),
                fromDTO(data.status)
            )
        } ?: E2EIModel(
            E2EIConfigModel(
                null,
                0
            ),
            Status.DISABLED
        )

    override fun fromModel(status: Status): FeatureFlagStatusDTO =
        when (status) {
            Status.ENABLED -> FeatureFlagStatusDTO.ENABLED
            Status.DISABLED -> FeatureFlagStatusDTO.DISABLED
        }

    override fun fromModel(model: MLSMigrationModel): FeatureConfigData.MLSMigration =
        FeatureConfigData.MLSMigration(
            MLSMigrationConfigDTO(
                model.startTime,
                model.endTime
            ),
            fromModel(model.status)
        )
}

fun MLSMigrationModel.toEntity(): MLSMigrationEntity =
    MLSMigrationEntity(
        status = status.equals(Status.ENABLED),
        startTime = startTime,
        endTime = endTime
    )

fun MLSMigrationEntity.toModel(): MLSMigrationModel =
    MLSMigrationModel(
        status = if (status) Status.ENABLED else Status.DISABLED,
        startTime = startTime,
        endTime = endTime
    )
