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

package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.AppLockConfigModel
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsConfigModel
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsModel
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesConfigModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
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
        val featureConfigModel = FeatureConfigModel(
            AppLockModel(
                AppLockConfigModel(true, 0),
                Status.ENABLED
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
            SelfDeletingMessagesModel(
                SelfDeletingMessagesConfigModel(0),
                Status.ENABLED
            ),
            ConfigsStatusModel(Status.ENABLED),
            ConfigsStatusModel(Status.ENABLED),
            ConfigsStatusModel(Status.ENABLED),
            MLSModel(
                emptyList(),
                Status.ENABLED
            )
        )

        val (arrangement, getFileSharingStatusUseCase) = Arrangement()
            .withSuccessfulResponse(featureConfigModel)
            .arrange()

        // When
        getFileSharingStatusUseCase.invoke()

        verify(arrangement.featureConfigRepository)
            .suspendFunction(arrangement.featureConfigRepository::getFeatureConfigs)
            .wasInvoked(exactly = once)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setFileSharingStatus)
            .with(eq(true), eq(false))
            .wasInvoked(exactly = once)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setMLSEnabled)
            .with(eq(false))
            .wasInvoked(exactly = once)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setConferenceCallingEnabled)
            .with(eq(true))
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

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        var kaliumConfigs: KaliumConfigs = KaliumConfigs()

        @Mock
        val isFileSharingEnabledUseCase = mock(classOf<IsFileSharingEnabledUseCase>())

        val syncFeatureConfigsUseCase =
            SyncFeatureConfigsUseCaseImpl(
                userConfigRepository,
                featureConfigRepository,
                isFileSharingEnabledUseCase,
                kaliumConfigs,
                TestUser.SELF.id
            )

        fun withSuccessfulResponse(expectedFileSharingModel: FeatureConfigModel): Arrangement {
            kaliumConfigs = KaliumConfigs()

            given(isFileSharingEnabledUseCase)
                .suspendFunction(isFileSharingEnabledUseCase::invoke)
                .whenInvoked().thenReturn(FileSharingStatus(true, null))
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setFileSharingStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setMLSEnabled)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setClassifiedDomainsStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setConferenceCallingEnabled)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(featureConfigRepository)
                .suspendFunction(featureConfigRepository::getFeatureConfigs).whenInvoked()
                .thenReturn(Either.Right(expectedFileSharingModel))

            return this
        }

        fun withGetFileSharingStatusErrorResponse(exception: KaliumException): Arrangement {
            kaliumConfigs = KaliumConfigs()

            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setFileSharingStatus)
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
