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
package com.wire.kalium.logic.data.mlsmigration

import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.featureConfigs.AppLockConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.ClassifiedDomainsConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.E2EIConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigApi
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigResponse
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.MLSConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.MLSMigrationConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.SelfDeletingMessagesConfigDTO
import com.wire.kalium.network.api.base.model.SupportedProtocolDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.MetadataDAO
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MLSMigrationRepositoryTest {
    @Test
    fun givenExistingConfiguration_whenFetchingMigrationConfiguration_thenConfigurationIsPersisted() = runTest {
        val (arrangement, mlsMigrationRepository) = Arrangement()
            .withFeatureConfigApiRequestSucceeds()
            .withMetaDataDaoInsertValue()
            .arrange()

        val result = mlsMigrationRepository.fetchMigrationConfiguration()

        result.shouldSucceed()

        verify(arrangement.metadataDAO)
            .suspendFunction(arrangement.metadataDAO::insertValue)
            .with(anything(), eq(MLSMigrationRepositoryImpl.MLS_MIGRATION_CONFIGURATION_KEY))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMLSConfiguration_whenSettingMigrationConfiguration_thenValueIsEncodedCorrectly() = runTest {
        val configuration = MLSMigrationModel(
            Clock.System.now(),
            Clock.System.now(),
            Status.ENABLED
        )
        val encodedValue = Json.encodeToString(MapperProvider.featureConfigMapper().fromModel(configuration))

        val (arrangement, mlsMigrationRepository) = Arrangement()
            .withMetaDataDaoInsertValue()
            .arrange()

        val result = mlsMigrationRepository.setMigrationConfiguration(configuration)
        result.shouldSucceed()

        verify(arrangement.metadataDAO)
            .suspendFunction(arrangement.metadataDAO::insertValue)
            .with(eq(encodedValue), eq(MLSMigrationRepositoryImpl.MLS_MIGRATION_CONFIGURATION_KEY))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMLSConfiguration_whenGettingMigrationConfiguration_thenValueIsDecodedCorrectly() = runTest {
        val configuration = MLSMigrationModel(
            Clock.System.now(),
            Clock.System.now(),
            Status.ENABLED
        )
        val encodedValue = Json.encodeToString(MapperProvider.featureConfigMapper().fromModel(configuration))

        val (arrangement, mlsMigrationRepository) = Arrangement()
            .withMetaDataDaoValueReturns(encodedValue)
            .arrange()

        val result = mlsMigrationRepository.getMigrationConfiguration()
        result.shouldSucceed()

        verify(arrangement.metadataDAO)
            .suspendFunction(arrangement.metadataDAO::valueByKey)
            .with(eq(MLSMigrationRepositoryImpl.MLS_MIGRATION_CONFIGURATION_KEY))
            .wasInvoked(exactly = once)

        assertEquals(configuration, result.getOrNull())
    }

    private class Arrangement {

        @Mock
        val featureConfigApi = mock(classOf<FeatureConfigApi>())

        @Mock
        val metadataDAO = mock(classOf<MetadataDAO>())

        fun withFeatureConfigApiRequestSucceeds() = apply {
            given(featureConfigApi).suspendFunction(featureConfigApi::featureConfigs)
                .whenInvoked()
                .thenReturn(NetworkResponse.Success(featureConfigResponse,  emptyMap(),200))
        }

        fun withMetaDataDaoValueReturns(value: String?) = apply {
            given(metadataDAO).suspendFunction(metadataDAO::valueByKey)
                .whenInvokedWith(anything())
                .thenReturn(value)
        }

        fun withMetaDataDaoInsertValue() = apply {
            given(metadataDAO).suspendFunction(metadataDAO::insertValue)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Unit)
        }

        fun arrange() = this to MLSMigrationRepositoryImpl(featureConfigApi, metadataDAO)

        companion object {
            val featureConfigResponse = FeatureConfigResponse(
                FeatureConfigData.AppLock(
                    AppLockConfigDTO(true, 0), FeatureFlagStatusDTO.ENABLED
                ),
                FeatureConfigData.ClassifiedDomains(ClassifiedDomainsConfigDTO(listOf()), FeatureFlagStatusDTO.ENABLED),
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
                        emptyList(),
                        ConvProtocol.MLS,
                        listOf(SupportedProtocolDTO.MLS),
                        emptyList(),
                        1
                    ), FeatureFlagStatusDTO.ENABLED),
                FeatureConfigData.E2EI(
                    E2EIConfigDTO(null),
                    FeatureFlagStatusDTO.ENABLED
                ),
                FeatureConfigData.MLSMigration(
                    MLSMigrationConfigDTO(Instant.DISTANT_FUTURE, Instant.DISTANT_FUTURE),
                    FeatureFlagStatusDTO.ENABLED
                )
            )
        }
    }
}
