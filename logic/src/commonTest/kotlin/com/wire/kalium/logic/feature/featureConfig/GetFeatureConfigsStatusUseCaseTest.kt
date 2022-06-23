package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.AppLockConfigModel
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsConfigModel
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesConfigModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class GetFeatureConfigsStatusUseCaseTest {

    @Test
    fun givenASuccessfulRepositoryResponse_whenInvokingTheUseCase_thenSuccessResultIsReturned() = runTest {
        // Given
        val fileSharingModel = FeatureConfigModel(
            AppLockModel(
                AppLockConfigModel(true, 0),
                "locked", "enabled"
            ),
            ClassifiedDomainsModel(
                ClassifiedDomainsConfigModel(listOf()),
                "locked", "enabled"
            ),
            ConfigsStatusModel("locked", "enabled"),
            ConfigsStatusModel("locked", "enabled"),
            ConfigsStatusModel("locked", "enabled"),
            ConfigsStatusModel("locked", "enabled"),
            ConfigsStatusModel("locked", "enabled"),
            ConfigsStatusModel("locked", "enabled"),
            SelfDeletingMessagesModel(
                SelfDeletingMessagesConfigModel(0),
                "locked", "enabled"
            ),
            ConfigsStatusModel("locked", "enabled"),
            ConfigsStatusModel("locked", "enabled"),
            ConfigsStatusModel("locked", "enabled")
        )

        val (arrangement, getFileSharingStatusUseCase) = Arrangement()
            .withSuccessfulResponse(fileSharingModel)
            .arrange()

        // When
        val actual = getFileSharingStatusUseCase.invoke()

        // Then
        assertTrue(actual is GetFeatureConfigStatusResult.Success)
        assertEquals(fileSharingModel, actual.featureConfigModel)

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
        val actual = getFileSharingStatusUseCase.invoke()

        // Then
        assertTrue(actual is GetFeatureConfigStatusResult.Failure.OperationDenied)
        assertEquals(arrangement.operationDeniedFailure, actual)

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
        val actual = getFileSharingStatusUseCase.invoke()

        // Then
        assertTrue(actual is GetFeatureConfigStatusResult.Failure.NoTeam)
        assertEquals(arrangement.noTeamFailure, actual)

        verify(arrangement.featureConfigRepository)
            .suspendFunction(arrangement.featureConfigRepository::getFeatureConfigs)
            .wasInvoked(exactly = once)
    }


    private class Arrangement {
        val operationDeniedFailure = GetFeatureConfigStatusResult.Failure.OperationDenied
        val noTeamFailure = GetFeatureConfigStatusResult.Failure.NoTeam

        @Mock
        val featureConfigRepository = mock(classOf<FeatureConfigRepository>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        @Mock
        val isFileSharingEnabledUseCase = mock(classOf<IsFileSharingEnabledUseCase>())

        val getFeatureConfigsStatusUseCase =
            GetFeatureConfigStatusUseCaseImpl(userConfigRepository, featureConfigRepository, isFileSharingEnabledUseCase)

        fun withSuccessfulResponse(expectedFileSharingModel: FeatureConfigModel): Arrangement {
            given(isFileSharingEnabledUseCase)
                .function(isFileSharingEnabledUseCase::invoke)
                .whenInvoked().thenReturn(true)

            given(userConfigRepository)
                .function(userConfigRepository::setFileSharingStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))

            given(featureConfigRepository)
                .suspendFunction(featureConfigRepository::getFeatureConfigs).whenInvoked()
                .thenReturn(Either.Right(expectedFileSharingModel))
            return this
        }

        fun withGetFileSharingStatusErrorResponse(exception: KaliumException): Arrangement {
            given(featureConfigRepository)
                .suspendFunction(featureConfigRepository::getFeatureConfigs)
                .whenInvoked()
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(exception)))
            return this
        }

        fun arrange() = this to getFeatureConfigsStatusUseCase
    }
}
