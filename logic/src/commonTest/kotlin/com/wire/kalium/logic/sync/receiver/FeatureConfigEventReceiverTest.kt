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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesConfigModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.feature.selfDeletingMessages.TeamSelfDeleteTimer
import com.wire.kalium.logic.feature.selfDeletingMessages.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.KaliumConfigStub
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class FeatureConfigEventReceiverTest {

    @Test
    fun givenMLSUpdatedEventGrantingAccessForSelfUser_whenProcessingEvent_ThenSetMLSEnabledToTrue() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSettingMLSEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.newMLSUpdatedEvent(MLSModel(listOf(TestUser.SELF.id.toPlainID()), Status.ENABLED))
        )

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setMLSEnabled)
            .with(eq(true))
            .wasInvoked(once)
    }

    @Test
    fun givenMLSUpdatedEventRemovingAccessForSelfUser_whenProcessingEvent_ThenSetMLSEnabledToFalse() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSettingMLSEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(arrangement.newMLSUpdatedEvent(MLSModel(emptyList(), Status.ENABLED)))

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setMLSEnabled)
            .with(eq(false))
            .wasInvoked(once)
    }

    @Suppress("MaxLineLength")
    @Test
    fun givenMLSUpdatedEventGrantingAccessForSelfUserButStatusIsDisabled_whenProcessingEvent_ThenSetMLSEnabledToFalse() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSettingMLSEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.newMLSUpdatedEvent(MLSModel(listOf(TestUser.SELF.id.toPlainID()), Status.DISABLED))
        )

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setMLSEnabled)
            .with(eq(false))
            .wasInvoked(once)
    }

    @Test
    fun givenFileSharingUpdatedEventWithStatusEnabled_whenProcessingEvent_ThenSetFileSharingStatusToTrue() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSettingFileSharingEnabledSuccessful()
            .withIsFileSharingEnabled(Either.Right(FileSharingStatus(state = FileSharingStatus.Value.Disabled, isStatusChanged = false)))
            .arrange()

        featureConfigEventReceiver.onEvent(arrangement.newFileSharingUpdatedEvent(ConfigsStatusModel(Status.ENABLED)))

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setFileSharingStatus)
            .with(eq(true), eq(true))
            .wasInvoked(once)
    }

    @Test
    fun givenFileSharingUpdatedEventWithStatusDisabled_whenProcessingEvent_ThenSetFileSharingStatusToFalse() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withIsFileSharingEnabled(Either.Right(FileSharingStatus(state = FileSharingStatus.Value.EnabledAll, isStatusChanged = false)))
            .withSettingFileSharingEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.newFileSharingUpdatedEvent(ConfigsStatusModel(Status.DISABLED))
        )

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setFileSharingStatus)
            .with(eq(false), eq(true))
            .wasInvoked(once)
    }

    @Test
    fun givenFileSharingUpdatedEvent_whenTheNewValueIsSameAsTHeOneStored_ThenIsChangedIsSetToFalse() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withIsFileSharingEnabled(Either.Right(FileSharingStatus(state = FileSharingStatus.Value.Disabled, isStatusChanged = false)))
            .withSettingFileSharingEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.newFileSharingUpdatedEvent(ConfigsStatusModel(Status.DISABLED))
        )

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setFileSharingStatus)
            .with(eq(false), eq(false))
            .wasInvoked(once)
    }

    @Test
    fun givenConferenceCallingUpdatedEventGrantingAccess_whenProcessingEvent_ThenSetConferenceCallingEnabledToTrue() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSettingConferenceCallingEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.newConferenceCallingUpdatedEvent(ConferenceCallingModel(Status.ENABLED))
        )

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setConferenceCallingEnabled)
            .with(eq(true))
            .wasInvoked(once)
    }

    @Test
    fun givenConferenceCallingUpdatedEventGrantingAccess_whenProcessingEvent_ThenSetConferenceCallingEnabledToFalse() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSettingConferenceCallingEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.newConferenceCallingUpdatedEvent(
                ConferenceCallingModel(Status.DISABLED)
            )
        )

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setConferenceCallingEnabled)
            .with(eq(false))
            .wasInvoked(once)
    }

    @Test
    fun givenNewTeamSettingsSelfDeletingDisablingEvent_whenProcessingEvent_ThenDisableFeatureOnUserConfigRepository() = runTest {
        val currentSelfDeletingMessagesStatus = TeamSettingsSelfDeletionStatus(
            enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Enabled,
            hasFeatureChanged = false
        )
        val newSelfDeletingEventModel = SelfDeletingMessagesModel(
            SelfDeletingMessagesConfigModel(
                enforcedTimeoutSeconds = null
            ), Status.DISABLED
        )
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSelfDeletingMessages(currentSelfDeletingMessagesStatus)
            .arrange()

        featureConfigEventReceiver.onEvent(arrangement.newSelfDeletingMessagesUpdatedEvent(newSelfDeletingEventModel))

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setTeamSettingsSelfDeletionStatus)
            .with(matching {
                it.hasFeatureChanged == true && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Disabled
            })
            .wasInvoked(once)
    }

    @Test
    fun givenNewSelfDeletingMessagesEnablingEventWithNoEnforcedTimeout_whenProcessingEvent_ThenEnableFeatureOnUserConfigRepository() =
        runTest {
            val currentSelfDeletingMessagesStatus = TeamSettingsSelfDeletionStatus(
                enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Enabled,
                hasFeatureChanged = false
            )
            val newSelfDeletingEventModel = SelfDeletingMessagesModel(
                SelfDeletingMessagesConfigModel(enforcedTimeoutSeconds = 0), Status.ENABLED
            )
            val (arrangement, featureConfigEventReceiver) = Arrangement()
                .withSelfDeletingMessages(currentSelfDeletingMessagesStatus)
                .arrange()

            featureConfigEventReceiver.onEvent(arrangement.newSelfDeletingMessagesUpdatedEvent(newSelfDeletingEventModel))

            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::setTeamSettingsSelfDeletionStatus)
                .with(matching {
                    it.hasFeatureChanged == false && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Enabled
                })
                .wasInvoked(once)
        }

    @Test
    fun givenNewSelfDeletingMessagesEnablingEventWithEnforcedTimeout_whenProcessingEvent_ThenEnableFeatureOnUserConfigRepository() =
        runTest {
            val newEnforcedDuration = 3600.toDuration(DurationUnit.SECONDS)
            val currentSelfDeletingMessagesStatus = TeamSettingsSelfDeletionStatus(
                enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Disabled,
                hasFeatureChanged = false
            )
            val newSelfDeletingEventModel = SelfDeletingMessagesModel(
                SelfDeletingMessagesConfigModel(enforcedTimeoutSeconds = newEnforcedDuration.inWholeSeconds), Status.ENABLED
            )
            val (arrangement, featureConfigEventReceiver) = Arrangement()
                .withSelfDeletingMessages(currentSelfDeletingMessagesStatus)
                .arrange()

            featureConfigEventReceiver.onEvent(arrangement.newSelfDeletingMessagesUpdatedEvent(newSelfDeletingEventModel))

            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::setTeamSettingsSelfDeletionStatus)
                .with(matching {
                    it.hasFeatureChanged == true && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Enforced
                            && (it.enforcedSelfDeletionTimer as TeamSelfDeleteTimer.Enforced).enforcedDuration == newEnforcedDuration
                })
                .wasInvoked(once)
        }

    @Test
    fun givenErrorWhenGettingStoredTeamSettingsSelfDeletionAndEnablingFeature_whenProcessingEvent_ThenEnableFeatureOnRepository() =
        runTest {
            val newEnforcedTimeoutSeconds = 3600L
            val newSelfDeletingEventModel = SelfDeletingMessagesModel(
                SelfDeletingMessagesConfigModel(enforcedTimeoutSeconds = newEnforcedTimeoutSeconds), Status.ENABLED
            )
            val (arrangement, featureConfigEventReceiver) = Arrangement()
                .withStoredTeamSettingsSelfDeletionStatusError()
                .arrange()

            featureConfigEventReceiver.onEvent(arrangement.newSelfDeletingMessagesUpdatedEvent(newSelfDeletingEventModel))

            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::setTeamSettingsSelfDeletionStatus)
                .with(matching {
                    it.hasFeatureChanged == true && it.enforcedSelfDeletionTimer !is TeamSelfDeleteTimer.Disabled
                })
                .wasInvoked(once)
        }

    @Test
    fun givenErrorWhenGettingStoredTeamSettingsSelfDeletionStatusAndDisablingFeature_whenProcessingEvent_ThenDisableFeatureOnRepository() =
        runTest {
            val newEnforcedTimeoutSeconds = 0L
            val newSelfDeletingEventModel = SelfDeletingMessagesModel(
                SelfDeletingMessagesConfigModel(enforcedTimeoutSeconds = newEnforcedTimeoutSeconds), Status.DISABLED
            )
            val (arrangement, featureConfigEventReceiver) = Arrangement()
                .withStoredTeamSettingsSelfDeletionStatusError()
                .arrange()

            featureConfigEventReceiver.onEvent(arrangement.newSelfDeletingMessagesUpdatedEvent(newSelfDeletingEventModel))

            verify(arrangement.userConfigRepository)
                .function(arrangement.userConfigRepository::setTeamSettingsSelfDeletionStatus)
                .with(matching<TeamSettingsSelfDeletionStatus> {
                    it.hasFeatureChanged == true && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Disabled
                })
                .wasInvoked(once)
        }

    @Test
    fun givenSelfDeletingFlagDisabledInKaliumConfigs_whenProcessingEnablingEvent_ThenItDisablesFeatureOnUserConfigRepository() = runTest {
        val newEnforcedTimeoutSeconds = 0L
        val newSelfDeletingEventModel = SelfDeletingMessagesModel(
            SelfDeletingMessagesConfigModel(enforcedTimeoutSeconds = newEnforcedTimeoutSeconds), Status.ENABLED
        )
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withDisabledKaliumConfigFlag()
            .arrange()

        featureConfigEventReceiver.onEvent(arrangement.newSelfDeletingMessagesUpdatedEvent(newSelfDeletingEventModel))

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setTeamSettingsSelfDeletionStatus)
            .with(matching<TeamSettingsSelfDeletionStatus> {
                it.hasFeatureChanged == null && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Disabled
            })
            .wasInvoked(once)
    }

    private class Arrangement {

        var kaliumConfigs = KaliumConfigStub()

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        private val featureConfigEventReceiver: FeatureConfigEventReceiver by lazy {
            FeatureConfigEventReceiverImpl(
                userConfigRepository,
                kaliumConfigs,
                TestUser.SELF.id
            )
        }

        fun withSettingMLSEnabledSuccessful() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::setMLSEnabled)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSettingFileSharingEnabledSuccessful() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::setFileSharingStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSettingConferenceCallingEnabledSuccessful() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::setConferenceCallingEnabled)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withIsFileSharingEnabled(result: Either<StorageFailure, FileSharingStatus>) = apply {
            given(userConfigRepository)
                .function(userConfigRepository::isFileSharingEnabled)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withSelfDeletingMessages(currentSelfDeletingMessagesStatus: TeamSettingsSelfDeletionStatus) = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::getTeamSettingsSelfDeletionStatus)
                .whenInvoked()
                .thenReturn(Either.Right(currentSelfDeletingMessagesStatus))
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setTeamSettingsSelfDeletionStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withStoredTeamSettingsSelfDeletionStatusError() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::getTeamSettingsSelfDeletionStatus)
                .whenInvoked()
                .thenReturn(Either.Left(StorageFailure.DataNotFound))
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setTeamSettingsSelfDeletionStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withDisabledKaliumConfigFlag() = apply {
            kaliumConfigs = kaliumConfigs.copy(selfDeletingMessages = false)
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setTeamSettingsSelfDeletionStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun newMLSUpdatedEvent(
            model: MLSModel
        ) = Event.FeatureConfig.MLSUpdated("eventId", false, model)

        fun newFileSharingUpdatedEvent(
            model: ConfigsStatusModel
        ) = Event.FeatureConfig.FileSharingUpdated("eventId", false, model)

        fun newConferenceCallingUpdatedEvent(
            model: ConferenceCallingModel
        ) = Event.FeatureConfig.ConferenceCallingUpdated("eventId", false, model)

        fun newSelfDeletingMessagesUpdatedEvent(
            model: SelfDeletingMessagesModel
        ) = Event.FeatureConfig.SelfDeletingMessagesConfig("eventId", false, model)

        fun arrange() = this to featureConfigEventReceiver
    }
}
