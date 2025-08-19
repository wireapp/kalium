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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.featureConfigs.AllowedGlobalOperationsConfigDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.AppLockConfigDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.ClassifiedDomainsConfigDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.ConferenceCallingConfigDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.E2EIConfigDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.authenticated.featureConfigs.FeatureConfigResponse
import com.wire.kalium.network.api.authenticated.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.MLSConfigDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.MLSMigrationConfigDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.SelfDeletingMessagesConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigApi
import com.wire.kalium.network.api.model.SupportedProtocolDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class FeatureConfigRepositoryTest {

    @Test
    fun whenFeatureConfigSuccess_thenTheSuccessIsReturned() = runTest {
        // Given
        val featureConfigModel = FeatureConfigModel(
            appLockModel = AppLockModel(
                status = Status.ENABLED,
                inactivityTimeoutSecs = 0
            ),
            classifiedDomainsModel = ClassifiedDomainsModel(
                ClassifiedDomainsConfigModel(listOf()),
                Status.ENABLED
            ),
            conferenceCallingModel = ConferenceCallingModel(Status.ENABLED, false),
            conversationGuestLinksModel = ConfigsStatusModel(Status.ENABLED),
            digitalSignaturesModel = ConfigsStatusModel(Status.ENABLED),
            fileSharingModel = ConfigsStatusModel(Status.ENABLED),
            guestRoomLinkModel = ConfigsStatusModel(Status.ENABLED),
            legalHoldModel = ConfigsStatusModel(Status.ENABLED),
            searchVisibilityModel = ConfigsStatusModel(Status.ENABLED),
            selfDeletingMessagesModel = SelfDeletingMessagesModel(
                SelfDeletingMessagesConfigModel(0),
                Status.ENABLED
            ),
            secondFactorPasswordChallengeModel = ConfigsStatusModel(Status.ENABLED),
            ssoModel = ConfigsStatusModel(Status.ENABLED),
            validateSAMLEmailsModel = ConfigsStatusModel(Status.ENABLED),
            mlsModel = MLSModel(
                defaultProtocol = SupportedProtocol.PROTEUS,
                supportedProtocols = setOf(SupportedProtocol.PROTEUS),
                status = Status.ENABLED,
                supportedCipherSuite = null
            ),
            e2EIModel = E2EIModel(
                E2EIConfigModel("url", 1000000L, false, null),
                Status.ENABLED
            ),
            mlsMigrationModel = MLSMigrationModel(
                Instant.DISTANT_FUTURE,
                Instant.DISTANT_FUTURE,
                Status.ENABLED
            ),
            channelsModel = ChannelFeatureConfiguration.Disabled,
            consumableNotificationsModel = ConfigsStatusModel(Status.DISABLED),
            allowedGlobalOperationsModel = AllowedGlobalOperationsModel(
                status = Status.DISABLED,
                mlsConversationsReset = false,
            ),
            cellsModel = CellsConfigModel(
                status = Status.DISABLED,
            ),
        )

        val expectedSuccess = Either.Right(featureConfigModel)
        val (arrangement, featureConfigRepository) = Arrangement().withSuccessfulResponse().arrange()

        // When
        val result = featureConfigRepository.getFeatureConfigs()

        // Then
        result.shouldSucceed { expectedSuccess.value }
        coVerify {
            arrangement.featureConfigApi.featureConfigs()
        }.wasInvoked(once)
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

        coVerify {
            arrangement.featureConfigApi.featureConfigs()
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.featureConfigApi.featureConfigs()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        val featureConfigResponse = FeatureConfigResponse(
            FeatureConfigData.AppLock(
                AppLockConfigDTO(true, 0), FeatureFlagStatusDTO.ENABLED
            ),
            FeatureConfigData.ClassifiedDomains(ClassifiedDomainsConfigDTO(listOf()), FeatureFlagStatusDTO.ENABLED),
            FeatureConfigData.ConferenceCalling(FeatureFlagStatusDTO.ENABLED, ConferenceCallingConfigDTO(false)),
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
                E2EIConfigDTO("url", null, false, 1000000L),
                FeatureFlagStatusDTO.ENABLED
            ),
            FeatureConfigData.MLSMigration(
                MLSMigrationConfigDTO(Instant.DISTANT_FUTURE, Instant.DISTANT_FUTURE),
                FeatureFlagStatusDTO.ENABLED
            ),
            FeatureConfigData.Channels(null, FeatureFlagStatusDTO.DISABLED),
            FeatureConfigData.AllowedGlobalOperations(
                AllowedGlobalOperationsConfigDTO(),
                FeatureFlagStatusDTO.DISABLED
            ),
            FeatureConfigData.ConsumableNotifications(FeatureFlagStatusDTO.DISABLED),
            FeatureConfigData.Cells(FeatureFlagStatusDTO.DISABLED),
        )

        val featureConfigApi: FeatureConfigApi = mock(FeatureConfigApi::class)

        var featureConfigRepository = FeatureConfigDataSource(featureConfigApi)

        suspend fun withSuccessfulResponse(): Arrangement {
            coEvery {
                featureConfigApi.featureConfigs()
            }.returns(NetworkResponse.Success(featureConfigResponse, mapOf(), 200))
            return this
        }

        suspend fun withErrorResponse(kaliumException: KaliumException): Arrangement {
            coEvery {
                featureConfigApi.featureConfigs()
            }.returns(NetworkResponse.Error(kaliumException))
            return this
        }

        fun arrange() = this to featureConfigRepository
    }
}
