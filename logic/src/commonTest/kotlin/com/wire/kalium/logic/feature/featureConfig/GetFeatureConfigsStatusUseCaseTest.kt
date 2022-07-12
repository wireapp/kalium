package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.AppLockConfigModel
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsConfigModel
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesConfigModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class GetFeatureConfigsStatusUseCaseTest {

    @Test
    fun givenASuccessfulRepositoryResponse_whenInvokingTheUseCase_thenSuccessResultIsReturned() = runTest {
        // Given
        val fileSharingModel = FeatureConfigModel(
            AppLockModel(
                AppLockConfigModel(true, 0),
                "enabled"
            ),
            ClassifiedDomainsModel(
                ClassifiedDomainsConfigModel(listOf()),
                "enabled"
            ),
            ConfigsStatusModel("enabled"),
            ConfigsStatusModel("enabled"),
            ConfigsStatusModel("enabled"),
            ConfigsStatusModel("enabled"),
            ConfigsStatusModel("enabled"),
            ConfigsStatusModel("enabled"),
            SelfDeletingMessagesModel(
                SelfDeletingMessagesConfigModel(0),
                "enabled"
            ),
            ConfigsStatusModel("enabled"),
            ConfigsStatusModel("enabled"),
            ConfigsStatusModel("enabled")
        )

        val (arrangement, getFileSharingStatusUseCase) = Arrangement()
            .withSuccessfulResponse(fileSharingModel)
            .arrange()

        // When
        getFileSharingStatusUseCase.invoke()

        verify(arrangement.featureConfigRepository)
            .suspendFunction(arrangement.featureConfigRepository::getFeatureConfigs)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFailWithInvalidCredentials_thenOperationDeniedIsReturned() = runTest {
        // Given
        val operationDeniedException = TestNetworkException.operationDenied
        val (arrangement, getFileSharingStatusUseCase) = Arrangement()
            .withGetFileSharingStatusErrorResponse(operationDeniedException)
            .arrange()

        // When
        getFileSharingStatusUseCase.invoke()

        // Then
        verify(arrangement.featureConfigRepository)
            .suspendFunction(arrangement.featureConfigRepository::getFeatureConfigs)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFailWithUserThatNotInTheTeam_thenNoTeamIsReturned() = runTest {
        // Given
        val noTeamException = TestNetworkException.noTeam
        val (arrangement, getFileSharingStatusUseCase) = Arrangement()
            .withGetFileSharingStatusErrorResponse(noTeamException)
            .arrange()

        // When
        getFileSharingStatusUseCase.invoke()

        // Then
        verify(arrangement.featureConfigRepository)
            .suspendFunction(arrangement.featureConfigRepository::getFeatureConfigs)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val featureConfigRepository = mock(classOf<FeatureConfigRepository>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        var kaliumConfigs: KaliumConfigs = KaliumConfigs()

        @Mock
        val isFileSharingEnabledUseCase = mock(classOf<IsFileSharingEnabledUseCase>())

        val syncFeatureConfigsUseCase =
            SyncFeatureConfigsUseCaseImpl(
                userConfigRepository, featureConfigRepository,
                isFileSharingEnabledUseCase, kaliumConfigs
            )

        fun withSuccessfulResponse(expectedFileSharingModel: FeatureConfigModel): Arrangement {
            kaliumConfigs = KaliumConfigs()

            given(isFileSharingEnabledUseCase)
                .function(isFileSharingEnabledUseCase::invoke)
                .whenInvoked().thenReturn(FileSharingStatus(true, null))
            given(userConfigRepository)
                .function(userConfigRepository::setFileSharingStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))

            given(featureConfigRepository)
                .suspendFunction(featureConfigRepository::getFeatureConfigs).whenInvoked()
                .thenReturn(Either.Right(expectedFileSharingModel))
            return this
        }

        fun withGetFileSharingStatusErrorResponse(exception: KaliumException): Arrangement {
            kaliumConfigs = KaliumConfigs()

            given(userConfigRepository)
                .function(userConfigRepository::setFileSharingStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))

            given(featureConfigRepository)
                .suspendFunction(featureConfigRepository::getFeatureConfigs)
                .whenInvoked()
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(exception)))
            return this
        }

        fun arrange() = this to syncFeatureConfigsUseCase
    }
}
