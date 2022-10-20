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
import com.wire.kalium.network.api.base.authenticated.featureConfigs.SelfDeletingMessagesConfigDTO
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
        assertEquals(listOf(PlainId("someId")), model.allowedUsers)
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
        assertEquals(true, model.config.enforceAppLock)
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
    fun givenApiModelResponse_whenMappingConferenceCallingToModel_thenShouldBeMappedCorrectly() {
        val (arrangement, mapper) = Arrangement().arrange()

        val model = mapper.fromDTO(arrangement.featureConfigResponse.conferenceCalling)

        assertEquals(Status.ENABLED, model.status)
    }

    private class Arrangement {
        val featureConfigResponse = FeatureConfigResponse(
            FeatureConfigData.AppLock(
                AppLockConfigDTO(true, 0), FeatureFlagStatusDTO.ENABLED
            ),
            FeatureConfigData.ClassifiedDomains(ClassifiedDomainsConfigDTO(listOf("wire.com")), FeatureFlagStatusDTO.ENABLED),
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
                    listOf("someId"),
                    ConvProtocol.MLS,
                    emptyList(),
                    1
                ), FeatureFlagStatusDTO.ENABLED
            )
        )

        val mapper: FeatureConfigMapper = FeatureConfigMapperImpl()

        fun arrange() = this to mapper
    }

}
