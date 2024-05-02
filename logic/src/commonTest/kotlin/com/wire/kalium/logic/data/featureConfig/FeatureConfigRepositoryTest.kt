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

import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
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
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class FeatureConfigRepositoryTest {

    @Test
    fun whenFeatureConfigSuccess_thenTheSuccessIsReturned() = runTest {
        // Given
        val featureConfigModel = FeatureConfigModel(
            AppLockModel(
                status = Status.ENABLED,
                inactivityTimeoutSecs = 0
            ),
            ClassifiedDomainsModel(
                ClassifiedDomainsConfigModel(listOf()),
                Status.ENABLED
            ),
            ConferenceCallingModel(Status.ENABLED),
            ConfigsStatusModel(Status.ENABLED),
            ConfigsStatusModel(Status.ENABLED),
            ConfigsStatusModel(Status.ENABLED),
            ConfigsStatusModel(Status.ENABLED),
            ConfigsStatusModel(Status.ENABLED),
            ConfigsStatusModel(Status.ENABLED),
            SelfDeletingMessagesModel(
                SelfDeletingMessagesConfigModel(0),
                Status.ENABLED
            ),
            ConfigsStatusModel(Status.ENABLED),
            ConfigsStatusModel(Status.ENABLED),
            ConfigsStatusModel(Status.ENABLED),
            MLSModel(
                defaultProtocol = SupportedProtocol.PROTEUS,
                supportedProtocols = setOf(SupportedProtocol.PROTEUS),
                status = Status.ENABLED,
                supportedCipherSuite = null
            ),
            E2EIModel(
                E2EIConfigModel("url", 1000000L),
                Status.ENABLED
            ),
            MLSMigrationModel(
                Instant.DISTANT_FUTURE,
                Instant.DISTANT_FUTURE,
                Status.ENABLED
            )
        )

        val expectedSuccess = Either.Right(featureConfigModel)
        val (arrangement, featureConfigRepository) = Arrangement().withSuccessfulResponse().arrange()

        // When
        val result = featureConfigRepository.getFeatureConfigs()

        // Then
        result.shouldSucceed { expectedSuccess.value }
        verify(arrangement.featureConfigApi)
            .suspendFunction(arrangement.featureConfigApi::featureConfigs)
            .wasInvoked(once)
    }

    @Test
    fun whenFeatureConfigFailWithOperationDeniedError_thenTheErrorIsPropagated() = runTest {
        // Given
        val operationDeniedException = TestNetworkException.operationDenied
        val (arrangement, featureConfigRepository) = Arrangement()
            .withErrorResponse(operationDeniedException).arrange()

        // When
        val result = featureConfigRepository.getFeatureConfigs()

        // Then
        result.shouldFail { Either.Left(operationDeniedException).value }

        verify(arrangement.featureConfigApi)
            .suspendFunction(arrangement.featureConfigApi::featureConfigs)
            .wasInvoked(exactly = once)
    }

    @Test
    fun whenFeatureConfigFailWithNoTeamError_thenTheErrorIsPropagated() = runTest {
        // Given
        val noTeamException = TestNetworkException.noTeam
        val (arrangement, featureConfigRepository) = Arrangement()
            .withErrorResponse(noTeamException).arrange()

        // When
        val result = featureConfigRepository.getFeatureConfigs()

        // Then
        result.shouldFail { Either.Left(noTeamException).value }

        verify(arrangement.featureConfigApi)
            .suspendFunction(arrangement.featureConfigApi::featureConfigs)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
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
                    SupportedProtocolDTO.PROTEUS,
                    listOf(SupportedProtocolDTO.PROTEUS),
                    allowedCipherSuites = listOf(1),
                    defaultCipherSuite = 1
                ), FeatureFlagStatusDTO.ENABLED
            ),
            FeatureConfigData.E2EI(
                E2EIConfigDTO("url", 1000000L),
                FeatureFlagStatusDTO.ENABLED
            ),
            FeatureConfigData.MLSMigration(
                MLSMigrationConfigDTO(Instant.DISTANT_FUTURE, Instant.DISTANT_FUTURE),
                FeatureFlagStatusDTO.ENABLED
            )
        )

        @Mock
        val featureConfigApi: FeatureConfigApi = mock(classOf<FeatureConfigApi>())

        var featureConfigRepository = FeatureConfigDataSource(featureConfigApi)

        fun withSuccessfulResponse(): Arrangement {
            given(featureConfigApi)
                .suspendFunction(featureConfigApi::featureConfigs).whenInvoked().then {
                    NetworkResponse.Success(featureConfigResponse, mapOf(), 200)
                }
            return this
        }

        fun withErrorResponse(kaliumException: KaliumException): Arrangement {
            given(featureConfigApi)
                .suspendFunction(featureConfigApi::featureConfigs)
                .whenInvoked()
                .then {
                    NetworkResponse.Error(
                        kaliumException
                    )
                }
            return this
        }

        fun arrange() = this to featureConfigRepository
    }
}
