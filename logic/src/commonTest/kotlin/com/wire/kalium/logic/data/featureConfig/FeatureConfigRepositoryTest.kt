package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.featureConfigs.AppLockConfigDTO
import com.wire.kalium.network.api.featureConfigs.ClassifiedDomainsConfigDTO
import com.wire.kalium.network.api.featureConfigs.FeatureConfigApi
import com.wire.kalium.network.api.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.featureConfigs.FeatureConfigResponse
import com.wire.kalium.network.api.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.featureConfigs.MLSConfigDTO
import com.wire.kalium.network.api.featureConfigs.SelfDeletingMessagesConfigDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureConfigRepositoryTest {

    @Test
    fun whenFeatureConfigSuccess_thenTheSuccessIsReturned() = runTest {
        // Given
        val featureConfigModel = FeatureConfigModel(
            AppLockModel(
                AppLockConfigModel(true, 0),
                Status.ENABLED
            ),
            ClassifiedDomainsModel(
                ClassifiedDomainsConfigModel(listOf()),
                Status.ENABLED
            ),
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
            ConfigsStatusModel(Status.ENABLED)
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
            FeatureConfigData.MLS(MLSConfigDTO(emptyList(), ConvProtocol.MLS, emptyList(), 1), FeatureFlagStatusDTO.ENABLED)
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
