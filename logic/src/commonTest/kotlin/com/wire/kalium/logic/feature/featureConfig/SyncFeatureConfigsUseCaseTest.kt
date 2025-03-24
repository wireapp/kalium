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
package com.wire.kalium.logic.feature.featureConfig

import app.cash.turbine.test
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.ChannelsConfigurationStorage
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.GuestRoomLinkStatus
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.ChannelFeatureConfiguration
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.E2EIConfigModel
import com.wire.kalium.logic.data.featureConfig.E2EIModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigTest
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesConfigModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.message.SelfDeletionMapper.toTeamSelfDeleteTimer
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.feature.channels.ChannelsFeatureConfigurationHandler
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ClassifiedDomainsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConferenceCallingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.E2EIConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SecondFactorPasswordChallengeConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SelfDeletingMessagesConfigHandler
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.config.inMemoryUserConfigStorage
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.unread.UserConfigDAO
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.doesNothing
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class SyncFeatureConfigsUseCaseTest {

    private val dispatcher = StandardTestDispatcher()

    private fun runTest(testBody: suspend TestScope.() -> Unit) = runTest(dispatcher, testBody = testBody)

    @Test
    fun givenSecondFactorChallengeIsEnabled_whenSyncing_thenItShouldBeStoredAsRequired() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsReturning(
                Either.Right(
                    FeatureConfigTest.newModel(
                        secondFactorPasswordChallengeModel = ConfigsStatusModel(Status.ENABLED)
                    )
                )
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isSecondFactorPasswordChallengeRequired().shouldSucceed {
            assertTrue(it)
        }
    }

    @Test
    fun givenSecondFactorChallengeIsDisabled_whenSyncing_thenItShouldBeStoredAsNotRequired() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsReturning(
                Either.Right(
                    FeatureConfigTest.newModel(
                        secondFactorPasswordChallengeModel = ConfigsStatusModel(Status.DISABLED)
                    )
                )
            )
            .withGetSupportedProtocolsReturning(null)
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isSecondFactorPasswordChallengeRequired().shouldSucceed {
            assertFalse(it)
        }
    }

    @Test
    fun givenConferenceCallingIsEnabled_whenSyncing_thenItShouldBeStoredAsEnabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(conferenceCallingModel = ConferenceCallingModel(Status.ENABLED, false))
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isConferenceCallingEnabled().shouldSucceed {
            assertTrue(it)
        }
    }

    @Test
    fun givenConferenceCallingIsDisasbled_whenSyncing_thenItShouldBeStoredAsDisabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(conferenceCallingModel = ConferenceCallingModel(Status.DISABLED, false))
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isConferenceCallingEnabled().shouldSucceed {
            assertFalse(it)
        }
    }

    @Test
    fun givenFileSharingIsEnabled_whenSyncing_thenShouldStoreAsEnabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.ENABLED))
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingStatus.Value.EnabledAll, it.state)
        }
    }

    @Test
    fun givenFileSharingIsEnabledWithoutChange_whenSyncing_thenShouldStoreAsDisabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.ENABLED))
            )
            .withLocalSharingEnabledReturning(
                status = true,
                isStatusChanged = false
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertFalse { it.isStatusChanged!! }
        }
    }

    @Test
    fun givenFileSharingIsDisabled_whenSyncing_thenShouldStoreAsDisabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.DISABLED))
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingStatus.Value.Disabled, it.state)
        }
    }

    @Test
    fun givenFileSharingIsDisabledWithoutChange_whenSyncing_thenShouldStoreChangedAsFalse() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.DISABLED))
            )
            .withLocalSharingEnabledReturning(
                status = false,
                isStatusChanged = false
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertFalse { it.isStatusChanged!! }
        }
    }

    @Test
    fun givenFileSharingChangedFromDisabledToEnabled_whenSyncing_thenShouldStoreChangedAsTrue() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.ENABLED))
            )
            .withLocalSharingEnabledReturning(
                status = false,
                isStatusChanged = false
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertTrue { it.isStatusChanged!! }
            FileSharingStatus(
                state = FileSharingStatus.Value.EnabledAll,
                isStatusChanged = false
            )
        }
    }

    @Test
    fun givenFileSharingChangedFromEnabledToDisabled_whenSyncing_thenShouldStoreChangedAsTrue() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.DISABLED))
            )
            .withLocalSharingEnabledReturning(
                status = true,
                isStatusChanged = false
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertTrue { it.isStatusChanged!! }
            assertEquals(FileSharingStatus.Value.Disabled, it.state)
        }
    }

    @Test
    fun givenGuestRoomLinkIsEnabledWithoutChange_whenSyncing_thenShouldStoreAsDisabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(guestRoomLink = ConfigsStatusModel(Status.ENABLED))
            )
            .withGuestRoomLinkEnabledReturning(
                GuestRoomLinkStatus(
                    isGuestRoomLinkEnabled = true,
                    isStatusChanged = false
                )
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.getGuestRoomLinkStatus().shouldSucceed {
            assertFalse { it.isStatusChanged!! }
        }
    }

    @Test
    fun givenGuestRoomLinkIsDisabledWithoutChange_whenSyncing_thenShouldStoreChangedAsFalse() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(guestRoomLink = ConfigsStatusModel(Status.DISABLED))
            )
            .withGuestRoomLinkEnabledReturning(
                GuestRoomLinkStatus(
                    isGuestRoomLinkEnabled = false,
                    isStatusChanged = false
                )
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.getGuestRoomLinkStatus().shouldSucceed {
            assertFalse { it.isStatusChanged!! }
        }
    }

    @Test
    fun givenGuestLinkChangedFromEnabledToDisabled_whenSyncing_thenShouldStoreChangedAsTrue() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(guestRoomLink = ConfigsStatusModel(Status.DISABLED))
            )
            .withGuestRoomLinkEnabledReturning(
                GuestRoomLinkStatus(
                    isGuestRoomLinkEnabled = true,
                    isStatusChanged = false
                )
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.getGuestRoomLinkStatus().shouldSucceed {
            assertTrue { it.isStatusChanged!! }
            assertFalse { it.isGuestRoomLinkEnabled!! }
        }
    }

    @Test
    fun givenFileGuestLinkChangedFromDisabledToEnabled_whenSyncing_thenShouldStoreChangedAsTrue() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(guestRoomLink = ConfigsStatusModel(Status.ENABLED))
            )
            .withGuestRoomLinkEnabledReturning(
                GuestRoomLinkStatus(
                    isGuestRoomLinkEnabled = false,
                    isStatusChanged = false
                )
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
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
        val (arrangement, getFileSharingStatusUseCase) = arrangement()
            .withRemoteFeatureConfigsReturning(Either.Left(NetworkFailure.ServerMiscommunication(operationDeniedException)))
            .arrange()

        // When
        getFileSharingStatusUseCase.invoke()

        // Then
        coVerify {
            arrangement.featureConfigRepository.getFeatureConfigs()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFailWithUserThatNotInTheTeam_thenNoTeamIsReturned() = runTest {
        // Given
        val noTeamException = TestNetworkException.noTeam
        val (arrangement, getFileSharingStatusUseCase) = arrangement()
            .withRemoteFeatureConfigsReturning(Either.Left(NetworkFailure.ServerMiscommunication(noTeamException)))
            .arrange()

        // When
        getFileSharingStatusUseCase.invoke()

        // Then
        coVerify {
            arrangement.featureConfigRepository.getFeatureConfigs()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRemoteConfigIsEnable_whenBuildConfigIsNoRestriction_thenStateIsEnableAll() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.ENABLED))
            )
            .withLocalSharingEnabledReturning(
                status = true,
                isStatusChanged = false
            )
            .withBuildConfigFileSharing(BuildFileRestrictionState.NoRestriction)
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingStatus.Value.EnabledAll, it.state)
        }
    }

    @Test
    fun givenRemoteConfigIsEnable_whenBuildConfigIsRestrictSome_thenStateIsRestrictSome() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.ENABLED))
            )
            .withLocalSharingEnabledReturning(
                status = true,
                isStatusChanged = false
            )
            .withBuildConfigFileSharing(BuildFileRestrictionState.AllowSome(listOf("png", "jpg")))
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingStatus.Value.EnabledSome(listOf("png", "jpg")), it.state)
        }
    }

    @Test
    fun givenRemoteConfigIsDisable_whenBuildConfigIsRestrictSome_thenStateIsDisable() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.DISABLED))
            )
            .withBuildConfigFileSharing(BuildFileRestrictionState.AllowSome(listOf("png", "jpg")))
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingStatus.Value.Disabled, it.state)
        }
    }

    @Test
    fun givenRemoteConfigIsDisable_whenBuildConfigIsNoRestriction_thenStateIsDisable() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(fileSharingModel = ConfigsStatusModel(Status.DISABLED))
            )
            .withBuildConfigFileSharing(BuildFileRestrictionState.NoRestriction)
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.isFileSharingEnabled().shouldSucceed {
            assertEquals(FileSharingStatus.Value.Disabled, it.state)
        }
    }

    @Test
    fun givenTeamSettingsSelfDeletionIsDisabledInKaliumConfigs_whenSyncing_thenItDisablesIt() = runTest {
        // Given
        val (arrangement, getTeamSettingsSelfDeletionStatusUseCase) = arrangement()
            .withKaliumConfigs { it.copy(selfDeletingMessages = false) }
            .withSetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        // When
        getTeamSettingsSelfDeletionStatusUseCase.invoke()

        // Then
        coVerify {
            arrangement.userConfigDAO.setTeamSettingsSelfDeletionStatus(
                matches {
                    it.isStatusChanged == null && it.selfDeletionTimerEntity.toTeamSelfDeleteTimer() == TeamSelfDeleteTimer.Disabled
                }
            )
        }
    }

    @Test
    fun givenNewEnabledWithNullEnforcedTimeoutTeamSettingsSelfDeletionEvent_whenSyncing_thenItIsJustEnabled() = runTest {
        // Given
        val expectedSelfDeletingMessagesModel = SelfDeletingMessagesModel(
            config = SelfDeletingMessagesConfigModel(null),
            status = Status.ENABLED
        )
        val (arrangement, getTeamSettingsSelfDeletionStatusUseCase) = arrangement()
            .withRemoteFeatureConfigsReturning(
                Either.Right(FeatureConfigTest.newModel(selfDeletingMessagesModel = expectedSelfDeletingMessagesModel))
            )
            .withSetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        // When
        getTeamSettingsSelfDeletionStatusUseCase.invoke()

        // Then
        coVerify {
            arrangement.userConfigDAO.setTeamSettingsSelfDeletionStatus(
                matches {
                    it.isStatusChanged == false && it.selfDeletionTimerEntity.toTeamSelfDeleteTimer() == TeamSelfDeleteTimer.Enabled
                }
            )
        }
    }

    @Test
    fun givenZeroEnforcedTeamSettingsSelfDeletionEvent_whenSyncing_thenItIsJustEnabled() = runTest {
        // Given
        val expectedSelfDeletingMessagesModel = SelfDeletingMessagesModel(
            config = SelfDeletingMessagesConfigModel(0L),
            status = Status.ENABLED
        )
        val (arrangement, getTeamSettingsSelfDeletionStatusUseCase) = arrangement()
            .withRemoteFeatureConfigsReturning(
                Either.Right(FeatureConfigTest.newModel(selfDeletingMessagesModel = expectedSelfDeletingMessagesModel))
            )
            .withSetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        // When
        getTeamSettingsSelfDeletionStatusUseCase.invoke()

        // Then
        coVerify {
            arrangement.userConfigDAO.setTeamSettingsSelfDeletionStatus(
                matches {
                    it.isStatusChanged == false && it.selfDeletionTimerEntity.toTeamSelfDeleteTimer() == TeamSelfDeleteTimer.Enabled
                }
            )
        }
    }

    @Test
    fun givenNewEnforcedTeamSettingsSelfDeletionEvent_whenSyncing_thenItMapsToEnforced() = runTest {
        // Given
        val enforcedTimeoutInMs = 3600000L
        val expectedSelfDeletingMessagesModel = SelfDeletingMessagesModel(
            config = SelfDeletingMessagesConfigModel(enforcedTimeoutInMs),
            status = Status.ENABLED
        )
        val (arrangement, getTeamSettingsSelfDeletionStatusUseCase) = arrangement()
            .withRemoteFeatureConfigsReturning(
                Either.Right(FeatureConfigTest.newModel(selfDeletingMessagesModel = expectedSelfDeletingMessagesModel))
            )
            .withSetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        // When
        getTeamSettingsSelfDeletionStatusUseCase.invoke()

        // Then
        coVerify {
            arrangement.userConfigDAO.setTeamSettingsSelfDeletionStatus(
                matches {
                    it.isStatusChanged == null && it.selfDeletionTimerEntity.toTeamSelfDeleteTimer() == TeamSelfDeleteTimer.Enforced(
                        enforcedTimeoutInMs.toDuration(
                            DurationUnit.MILLISECONDS
                        )
                    )
                }
            )
        }
    }

    @Test
    fun givenE2EIIsDisabled_whenSyncing_thenItShouldBeStoredAsDisabled() = runTest {
        val e2EIModel = E2EIModel(E2EIConfigModel("url", 10_000L, false, null), Status.DISABLED)
        val expectedGracePeriodEnd = DateTimeUtil.currentInstant().plus(10_000.toDuration(DurationUnit.SECONDS))
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(e2EIModel = e2EIModel)
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.getE2EISettings().shouldSucceed {
            assertFalse(it.isRequired)
            assertEquals("url", it.discoverUrl)

            val gracePeriodsDiff = it.gracePeriodEnd?.minus(expectedGracePeriodEnd)?.inWholeMilliseconds ?: Long.MAX_VALUE
            assertTrue(gracePeriodsDiff > -1000 || gracePeriodsDiff < 1000)
        }
    }


    @Test
    fun givenE2EIIsEnabled_whenSyncing_thenItShouldBeStoredAsEnabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel()
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.userConfigRepository.getE2EISettings().shouldSucceed {
            assertTrue(it.isRequired)
        }
    }

    @Test
    fun givenChannelsAreDisabled_whenSyncing_thenItShouldBeStoredAsDisabled() = runTest {
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(channelFeatureConfiguration = ChannelFeatureConfiguration.Disabled)
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.channelsConfigurationStorage.observePersistedChannelsConfiguration().test {
            assertEquals(ChannelFeatureConfiguration.Disabled, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenChannelsAreEnabled_whenSyncing_thenItShouldBeStoredCorrectlyAsEnabled() = runTest {
        val expectedConfiguration = ChannelFeatureConfiguration.Enabled(
            ChannelFeatureConfiguration.TeamUserType.ADMINS_ONLY, ChannelFeatureConfiguration.TeamUserType.EVERYONE_IN_THE_TEAM
        )
        val (arrangement, syncFeatureConfigsUseCase) = arrangement()
            .withRemoteFeatureConfigsSucceeding(
                FeatureConfigTest.newModel(channelFeatureConfiguration = expectedConfiguration)
            )
            .withGetTeamSettingsSelfDeletionStatusSuccessful()
            .withGetSupportedProtocolsReturning(null)
            .arrange()

        syncFeatureConfigsUseCase()

        arrangement.channelsConfigurationStorage.observePersistedChannelsConfiguration().test {
            assertEquals(expectedConfiguration, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun TestScope.arrangement() = Arrangement(coroutineContext[CoroutineDispatcher]!! as TestDispatcher)

    private class Arrangement(dispatcher: TestDispatcher) {
        private val inMemoryStorage = inMemoryUserConfigStorage()
        private val userDatabase = TestUserDatabase(TestUser.ENTITY_ID, dispatcher)
        val channelsConfigurationStorage = ChannelsConfigurationStorage(userDatabase.builder.metadataDAO)
        var kaliumConfigs = KaliumConfigs()

        @Mock
        val userConfigDAO: UserConfigDAO = mock(UserConfigDAO::class)

        var userConfigRepository: UserConfigRepository = UserConfigDataSource(
            inMemoryStorage,
            userConfigDAO,
            kaliumConfigs
        )
            private set

        @Mock
        val featureConfigRepository = mock(FeatureConfigRepository::class)

        @Mock
        val updateSupportedProtocolsAndResolveOneOnOnes = mock(UpdateSupportedProtocolsAndResolveOneOnOnesUseCase::class)

        private lateinit var syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase

        init {
            runBlocking {
                withRemoteFeatureConfigsReturning(Either.Right(FeatureConfigTest.newModel()))

                withGuestRoomLinkEnabledReturning(
                    GuestRoomLinkStatus(
                        isGuestRoomLinkEnabled = true,
                        isStatusChanged = false
                    )
                )
            }
        }

        fun withBuildConfigFileSharing(
            state: BuildFileRestrictionState
        ) = apply {
            kaliumConfigs = kaliumConfigs.copy(fileRestrictionState = state)
            userConfigRepository = UserConfigDataSource(
                inMemoryStorage,
                userConfigDAO,
                kaliumConfigs
            )
        }

        suspend fun withRemoteFeatureConfigsSucceeding(featureConfigModel: FeatureConfigModel) =
            withRemoteFeatureConfigsReturning(Either.Right(featureConfigModel))

        suspend fun withRemoteFeatureConfigsReturning(result: Either<NetworkFailure, FeatureConfigModel>) = apply {
            coEvery {
                featureConfigRepository.getFeatureConfigs()
            }.returns(result)
        }

        fun withLocalSharingEnabledReturning(
            status: Boolean,
            isStatusChanged: Boolean?
        ) = apply {
            userConfigRepository.setFileSharingStatus(
                status,
                isStatusChanged
            )
        }

        fun withGuestRoomLinkEnabledReturning(guestRoomLinkStatus: GuestRoomLinkStatus) = apply {
            inMemoryStorage.persistGuestRoomLinkFeatureFlag(
                guestRoomLinkStatus.isGuestRoomLinkEnabled ?: false,
                guestRoomLinkStatus.isStatusChanged
            )
        }

        suspend fun withGetTeamSettingsSelfDeletionStatusSuccessful() = apply {
            coEvery {
                userConfigDAO.getTeamSettingsSelfDeletionStatus()
            }.returns(null)
        }

        suspend fun withSetTeamSettingsSelfDeletionStatusSuccessful() = apply {
            coEvery {
                userConfigDAO.setTeamSettingsSelfDeletionStatus(any())
            }.doesNothing()
        }

        suspend fun withGetSupportedProtocolsReturning(result: Set<SupportedProtocolEntity>?) = apply {
            coEvery {
                userConfigDAO.getSupportedProtocols()
            }.returns(result)
        }

        fun withKaliumConfigs(changeConfigs: (KaliumConfigs) -> KaliumConfigs) = apply {
            this.kaliumConfigs = changeConfigs(this.kaliumConfigs)
        }

        fun arrange(): Pair<Arrangement, SyncFeatureConfigsUseCase> {
            syncFeatureConfigsUseCase = SyncFeatureConfigsUseCaseImpl(
                featureConfigRepository,
                GuestRoomConfigHandler(userConfigRepository, kaliumConfigs),
                FileSharingConfigHandler(userConfigRepository),
                MLSConfigHandler(userConfigRepository, updateSupportedProtocolsAndResolveOneOnOnes),
                MLSMigrationConfigHandler(userConfigRepository, updateSupportedProtocolsAndResolveOneOnOnes),
                ClassifiedDomainsConfigHandler(userConfigRepository),
                ConferenceCallingConfigHandler(userConfigRepository),
                SecondFactorPasswordChallengeConfigHandler(userConfigRepository),
                SelfDeletingMessagesConfigHandler(userConfigRepository, kaliumConfigs),
                E2EIConfigHandler(userConfigRepository),
                AppLockConfigHandler(userConfigRepository),
                ChannelsFeatureConfigurationHandler(channelsConfigurationStorage)
            )
            return this to syncFeatureConfigsUseCase
        }

    }
}
