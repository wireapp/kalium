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
import com.wire.kalium.logic.configuration.FileSharingState
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.GuestRoomLinkStatus
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigTest
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
import com.wire.kalium.logic.feature.user.guestroomlink.GetGuestRoomLinkFeatureStatusUseCase
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.config.inMemoryUserConfigStorage
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncFeatureConfigsUseCaseTest {

    @Test
    fun givenSecondFactorChallengeIsEnabled_whenSyncing_thenItShouldBeStoredAsRequired() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsReturning(
                Either.Right(
                    FeatureConfigTest.newModel(
                        secondFactorPasswordChallengeModel = ConfigsStatusModel(Status.ENABLED)
                    )
                )
            ).arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isSecondFactorPasswordChallengeRequired().shouldSucceed {
            assertTrue(it)
        }
    }

    @Test
    fun givenSecondFactorChallengeIsDisabled_whenSyncing_thenItShouldBeStoredAsNotRequired() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsReturning(
                Either.Right(
                    FeatureConfigTest.newModel(
                        secondFactorPasswordChallengeModel = ConfigsStatusModel(Status.DISABLED)
                    )
                )
            ).arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isSecondFactorPasswordChallengeRequired().shouldSucceed {
            assertFalse(it)
        }
    }

    @Test
    fun givenMlsIsEnabledAndSelfUserIsWhitelisted_whenSyncing_thenItShouldBeStoredAsEnabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(mlsModel = MLSModel(listOf(SELF_USER_ID.toPlainID()), Status.ENABLED))
            ).arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isMLSEnabled().shouldSucceed {
            assertTrue(it)
        }
    }

    @Test
    fun givenMlsIsEnabledAndSelfUserIsNotWhitelisted_whenSyncing_thenItShouldBeStoredAsDisabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(mlsModel = MLSModel(listOf(), Status.ENABLED))
            ).arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isMLSEnabled().shouldSucceed {
            assertFalse(it)
        }
    }

    @Test
    fun givenMlsIsDisasbled_whenSyncing_thenItShouldBeStoredAsDisabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(mlsModel = MLSModel(listOf(), Status.DISABLED))
            ).arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isMLSEnabled().shouldSucceed {
            assertFalse(it)
        }
    }

    @Test
    fun givenConferenceCallingIsEnabled_whenSyncing_thenItShouldBeStoredAsEnabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(conferenceCallingModel = ConferenceCallingModel(Status.ENABLED))
            ).arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isConferenceCallingEnabled().shouldSucceed {
            assertTrue(it)
        }
    }

    @Test
    fun givenConferenceCallingIsDisasbled_whenSyncing_thenItShouldBeStoredAsDisabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(conferenceCallingModel = ConferenceCallingModel(Status.DISABLED))
            ).arrange()


        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isConferenceCallingEnabled().shouldSucceed {
            assertFalse(it)
        }
    }

    @Test
    fun givenFileSharingIsEnabled_whenSyncing_thenShouldStoreAsEnabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.ENABLED))
            ).arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingState.EnabledAll, it.state)
        }
    }

    @Test
    fun givenFileSharingIsEnabledWithoutChange_whenSyncing_thenShouldStoreAsDisabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.ENABLED))
            )
            .withLocalSharingEnabledReturning(
                FileSharingStatus(
                    state = FileSharingState.EnabledAll,
                    isStatusChanged = false
                )
            )
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertFalse { it.isStatusChanged!! }
        }
    }

    @Test
    fun givenFileSharingIsDisabled_whenSyncing_thenShouldStoreAsDisabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.DISABLED))
            ).arrange()


        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingState.Disabled, it.state)
        }
    }

    @Test
    fun givenFileSharingIsDisabledWithoutChange_whenSyncing_thenShouldStoreChangedAsFalse() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.DISABLED))
            )
            .withLocalSharingEnabledReturning(
                FileSharingStatus(
                    state = FileSharingState.Disabled,
                    isStatusChanged = false
                )
            )
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertFalse { it.isStatusChanged!! }
        }
    }

    @Test
    fun givenFileSharingChangedFromDisabledToEnabled_whenSyncing_thenShouldStoreChangedAsTrue() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.ENABLED))
            )
            .withLocalSharingEnabledReturning(
                FileSharingStatus(
                    state = FileSharingState.Disabled,
                    isStatusChanged = false
                )
            )
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertTrue { it.isStatusChanged!! }
            FileSharingStatus(
                state = FileSharingState.EnabledAll,
                isStatusChanged = false
            )
        }
    }

    @Test
    fun givenFileSharingChangedFromEnabledToDisabled_whenSyncing_thenShouldStoreChangedAsTrue() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.DISABLED))
            )
            .withLocalSharingEnabledReturning(
                FileSharingStatus(
                    state = FileSharingState.EnabledAll,
                    isStatusChanged = false
                )
            )
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertTrue { it.isStatusChanged!! }
            assertEquals(FileSharingState.Disabled, it.state)
        }
    }

    @Test
    fun givenGuestRoomLinkIsEnabledWithoutChange_whenSyncing_thenShouldStoreAsDisabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(guestRoomLink = ConfigsStatusModel(Status.ENABLED))
            )
            .withGuestRoomLinkEnabledReturning(
                GuestRoomLinkStatus(
                    isGuestRoomLinkEnabled = true,
                    isStatusChanged = false
                )
            )
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.getGuestRoomLinkStatus().shouldSucceed {
            assertFalse { it.isStatusChanged!! }
        }
    }

    @Test
    fun givenGuestRoomLinkIsDisabledWithoutChange_whenSyncing_thenShouldStoreChangedAsFalse() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(guestRoomLink = ConfigsStatusModel(Status.DISABLED))
            )
            .withGuestRoomLinkEnabledReturning(
                GuestRoomLinkStatus(
                    isGuestRoomLinkEnabled = false,
                    isStatusChanged = false
                )
            )
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.getGuestRoomLinkStatus().shouldSucceed {
            assertFalse { it.isStatusChanged!! }
        }
    }

    @Test
    fun givenGuestLinkChangedFromEnabledToDisabled_whenSyncing_thenShouldStoreChangedAsTrue() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(guestRoomLink = ConfigsStatusModel(Status.DISABLED))
            )
            .withGuestRoomLinkEnabledReturning(
                GuestRoomLinkStatus(
                    isGuestRoomLinkEnabled = true,
                    isStatusChanged = false
                )
            )
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.getGuestRoomLinkStatus().shouldSucceed {
            assertTrue { it.isStatusChanged!! }
            assertFalse { it.isGuestRoomLinkEnabled!! }
        }
    }

    @Test
    fun givenFileGuestLinkChangedFromDisabledToEnabled_whenSyncing_thenShouldStoreChangedAsTrue() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(guestRoomLink = ConfigsStatusModel(Status.ENABLED))
            )
            .withGuestRoomLinkEnabledReturning(
                GuestRoomLinkStatus(
                    isGuestRoomLinkEnabled = false,
                    isStatusChanged = false
                )
            )
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.getGuestRoomLinkStatus().shouldSucceed {
            assertTrue { it.isStatusChanged!! }
            assertTrue { it.isGuestRoomLinkEnabled!! }
        }
    }

    @Test
    fun givenRepositoryCallFailWithInvalidCredentials_thenOperationDeniedIsReturned() = runTest {
        // Given
        val operationDeniedException = TestNetworkException.operationDenied
        val (arrangement, getFileSharingStatusUseCase) = Arrangement()
            .withRemoteFeatureConfigsReturning(Either.Left(NetworkFailure.ServerMiscommunication(operationDeniedException)))
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
            .withRemoteFeatureConfigsReturning(Either.Left(NetworkFailure.ServerMiscommunication(noTeamException)))
            .arrange()

        // When
        getFileSharingStatusUseCase.invoke()

        // Then
        verify(arrangement.featureConfigRepository)
            .suspendFunction(arrangement.featureConfigRepository::getFeatureConfigs)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRemoteFeatureConfigsEnables_whenLocalIsEnableSome_thenIsStatusChangedIsFalse() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.ENABLED))
            )
            .withLocalSharingEnabledReturning(
                FileSharingStatus(
                    state = FileSharingState.EnabledSome(listOf("png", "jpg")),
                    isStatusChanged = false
                )
            )
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingState.EnabledAll, it.state)
            assertFalse { it.isStatusChanged!! }
        }
    }

    @Test
    fun givenRemoteFeatureConfigsDisble_whenLocalIsEnableSome_thenIsStatusChangedIsTrue() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.DISABLED))
            )
            .withLocalSharingEnabledReturning(
                FileSharingStatus(
                    state = FileSharingState.EnabledSome(listOf("png", "jpg")),
                    isStatusChanged = false
                )
            )
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertTrue { it.isStatusChanged!! }
        }
    }

    @Test
    fun givenRemoteConfigIsEnable_whenBuildConfigIsNoRestriction_thenStateIsEnableAll() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.ENABLED))
            )
            .withBuildConfigFileSharing(BuildFileRestrictionState.NoRestriction)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingState.EnabledAll, it.state)
        }
    }

    @Test
    fun givenRemoteConfigIsEnable_whenBuildConfigIsRestrictSome_thenStateIsRestrictSome() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.ENABLED))
            )
            .withBuildConfigFileSharing(BuildFileRestrictionState.AllowSome(listOf("png", "jpg")))
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingState.EnabledSome(listOf("png", "jpg")), it.state)
        }
    }

    @Test
    fun givenRemoteConfigIsDisable_whenBuildConfigIsRestrictSome_thenStateIsDisable() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.DISABLED))
            )
            .withBuildConfigFileSharing(BuildFileRestrictionState.AllowSome(listOf("png", "jpg")))
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingState.Disabled, it.state)
        }
    }

    @Test
    fun givenRemoteConfigIsDisable_whenBuildConfigIsNoRestriction_thenStateIsDisable() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = Arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.DISABLED))
            )
            .withBuildConfigFileSharing(BuildFileRestrictionState.NoRestriction)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingState.Disabled, it.state)
        }
    }

    private class Arrangement {

        var kaliumConfigs = KaliumConfigs()

        lateinit var userConfigRepository: UserConfigRepository
            private set

        @Mock
        val featureConfigRepository = mock(classOf<FeatureConfigRepository>())

        @Mock
        val isFileSharingEnabledUseCase = mock(classOf<IsFileSharingEnabledUseCase>())

        @Mock
        val isGuestRoomLinkFeatureEnabled = mock(classOf<GetGuestRoomLinkFeatureStatusUseCase>())

        private val syncFeatureConfigsUseCase
            get() = SyncFeatureConfigsUseCaseImpl(
                userConfigRepository,
                featureConfigRepository,
                isFileSharingEnabledUseCase,
                isGuestRoomLinkFeatureEnabled,
                kaliumConfigs,
                SELF_USER_ID
            )

        init {
            withRemoteFeatureConfigsReturning(Either.Right(FeatureConfigTest.newModel()))
            withLocalSharingEnabledReturning(
                FileSharingStatus(
                    state = FileSharingState.EnabledAll,
                    isStatusChanged = false
                )
            )
            withGuestRoomLinkEnabledReturning(
                GuestRoomLinkStatus(
                    isGuestRoomLinkEnabled = true,
                    isStatusChanged = false
                )
            )
        }

        fun withBuildConfigFileSharing(
            state: BuildFileRestrictionState
        ) = apply {
            kaliumConfigs = kaliumConfigs.copy(fileRestrictionState = state)
        }

        fun withRemoteFeatureConfigsSucceeding(featureConfigModel: FeatureConfigModel) =
            withRemoteFeatureConfigsReturning(Either.Right(featureConfigModel))

        fun withRemoteFeatureConfigsReturning(result: Either<NetworkFailure, FeatureConfigModel>) = apply {
            given(featureConfigRepository)
                .suspendFunction(featureConfigRepository::getFeatureConfigs)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withLocalSharingEnabledReturning(fileSharingStatus: FileSharingStatus) = apply {
            given(isFileSharingEnabledUseCase)
                .function(isFileSharingEnabledUseCase::invoke)
                .whenInvoked()
                .thenReturn(fileSharingStatus)
        }

        fun withGuestRoomLinkEnabledReturning(guestRoomLinkStatus: GuestRoomLinkStatus) = apply {
            given(isGuestRoomLinkFeatureEnabled)
                .function(isGuestRoomLinkFeatureEnabled::invoke)
                .whenInvoked()
                .thenReturn(guestRoomLinkStatus)
        }

        fun withKaliumConfigs(changeConfigs: (KaliumConfigs) -> KaliumConfigs) = apply {
            this.kaliumConfigs = changeConfigs(this.kaliumConfigs)
        }

        fun arrange(): Pair<Arrangement, SyncFeatureConfigsUseCaseImpl> {
            userConfigRepository = UserConfigDataSource(
                inMemoryUserConfigStorage(),
                kaliumConfigs
            )
            return this to syncFeatureConfigsUseCase
        }

    }

    private companion object {
        val SELF_USER_ID = TestUser.USER_ID
    }
}
