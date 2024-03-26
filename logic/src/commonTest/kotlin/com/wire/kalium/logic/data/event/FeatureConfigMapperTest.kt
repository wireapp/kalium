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

package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.data.featureConfig.FeatureConfigMapper
import com.wire.kalium.logic.data.featureConfig.FeatureConfigMapperImpl
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.featureConfigs.AppLockConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.ClassifiedDomainsConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigResponse
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.MLSConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.E2EIConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.MLSMigrationConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.SelfDeletingMessagesConfigDTO
import com.wire.kalium.network.api.base.model.SupportedProtocolDTO
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureConfigMapperTest {

    @Test
    fun givenApiModelResponse_whenMappingToModel_thenASingleFieldsShouldBeMappedCorrectly() {
        val (arrangement, mapper) = Arrangement().arrange()

        val model = mapper.fromDTO(arrangement.featureConfigResponse)

        assertEquals(Status.ENABLED, model.fileSharingModel.status)
    }

    @Test
    fun givenApiModelResponse_whenMappingStatusToModel_thenShouldBeMappedCorrectly() {
        val (_, mapper) = Arrangement().arrange()

        val model = mapper.fromDTO(FeatureFlagStatusDTO.DISABLED)

        assertEquals(Status.DISABLED, model)
    }

    @Test
    fun givenApiModelResponse_whenMappingMLSStatusToModel_thenShouldBeMappedCorrectly() {
        val (arrangement, mapper) = Arrangement().arrange()

        val model = mapper.fromDTO(arrangement.featureConfigResponse.mls)

        assertEquals(Status.ENABLED, model.status)
    }

    @Test
    fun givenApiModelResponse_whenMappingMLSMigrationStatusToModel_thenShouldBeMappedCorrectly() {
        val (arrangement, mapper) = Arrangement().arrange()

        val model = arrangement.featureConfigResponse.mlsMigration?.let { mapper.fromDTO(it) }

        assertEquals(Status.ENABLED, model?.status)
        assertEquals(Instant.DISTANT_FUTURE, model?.startTime)
        assertEquals(Instant.DISTANT_FUTURE, model?.endTime)
    }

    @Test
    fun givenApiModelResponse_whenMappingClassifiedDomainsToModel_thenShouldBeMappedCorrectly() {
        val (arrangement, mapper) = Arrangement().arrange()

        val model = mapper.fromDTO(arrangement.featureConfigResponse.classifiedDomains)

        assertEquals(Status.ENABLED, model.status)
        assertEquals(listOf("wire.com"), model.config.domains)
    }

    @Test
    fun givenApiModelResponse_whenMappingAppLockToModel_thenShouldBeMappedCorrectly() {
        val (arrangement, mapper) = Arrangement().arrange()

        val model = mapper.fromDTO(arrangement.featureConfigResponse.appLock)

        assertEquals(Status.ENABLED, model.status)
    }

    @Test
    fun givenApiModelResponse_whenMappingSelfDeletingMessagesToModel_thenShouldBeMappedCorrectly() {
        val (arrangement, mapper) = Arrangement().arrange()

        val model = mapper.fromDTO(arrangement.featureConfigResponse.selfDeletingMessages)

        assertEquals(Status.ENABLED, model.status)
        assertEquals(0, model.config.enforcedTimeoutSeconds)
    }

    @Test
    fun givenApiModelResponse_whenMappingFileSharingToModel_thenShouldBeMappedCorrectly() {
        val (arrangement, mapper) = Arrangement().arrange()

        val model = mapper.fromDTO(arrangement.featureConfigResponse.fileSharing)

        assertEquals(Status.ENABLED, model.status)
    }

    @Test
    fun givenApiModelResponse_whenMappingGuestRoomLinkToModel_thenShouldBeMappedCorrectly() {
        val (arrangement, mapper) = Arrangement().arrange()

        val model = mapper.fromDTO(arrangement.featureConfigResponse.conversationGuestLinks)

        assertEquals(Status.ENABLED, model.status)
    }

    @Test
    fun givenApiModelResponse_whenMappingConferenceCallingToModel_thenShouldBeMappedCorrectly() {
        val (arrangement, mapper) = Arrangement().arrange()

        val model = mapper.fromDTO(arrangement.featureConfigResponse.conferenceCalling)

        assertEquals(Status.ENABLED, model.status)
    }

    private class Arrangement {
        val featureConfigResponse = FeatureConfigResponse(
            FeatureConfigData.AppLock(
                AppLockConfigDTO(true, 0),
                FeatureFlagStatusDTO.ENABLED
            ),
            FeatureConfigData.ClassifiedDomains(
                ClassifiedDomainsConfigDTO(listOf("wire.com")),
                FeatureFlagStatusDTO.ENABLED
            ),
            FeatureConfigData.ConferenceCalling(FeatureFlagStatusDTO.ENABLED),
            FeatureConfigData.ConversationGuestLinks(FeatureFlagStatusDTO.ENABLED),
            FeatureConfigData.DigitalSignatures(FeatureFlagStatusDTO.ENABLED),
            FeatureConfigData.FileSharing(FeatureFlagStatusDTO.ENABLED),
            FeatureConfigData.Legalhold(FeatureFlagStatusDTO.ENABLED),
            FeatureConfigData.SearchVisibility(FeatureFlagStatusDTO.ENABLED),
            FeatureConfigData.SelfDeletingMessages(SelfDeletingMessagesConfigDTO(0), FeatureFlagStatusDTO.ENABLED),
            FeatureConfigData.SecondFactorPasswordChallenge(FeatureFlagStatusDTO.ENABLED),
            FeatureConfigData.SSO(FeatureFlagStatusDTO.ENABLED),
            FeatureConfigData.ValidateSAMLEmails(FeatureFlagStatusDTO.ENABLED),
            FeatureConfigData.MLS(
                MLSConfigDTO(
                    SupportedProtocolDTO.MLS,
                    listOf(SupportedProtocolDTO.MLS),
                    emptyList(),
                    1
                ), FeatureFlagStatusDTO.ENABLED
            ),
            FeatureConfigData.E2EI(
                E2EIConfigDTO("url", 1_000_000L),
                FeatureFlagStatusDTO.ENABLED
            ),
            FeatureConfigData.MLSMigration(
                MLSMigrationConfigDTO(
                    Instant.DISTANT_FUTURE,
                    Instant.DISTANT_FUTURE
                ),
                FeatureFlagStatusDTO.ENABLED
            )
        )

        val mapper: FeatureConfigMapper = FeatureConfigMapperImpl()

        fun arrange() = this to mapper
    }
}
