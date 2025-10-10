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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesConfigModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ClassifiedDomainsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConferenceCallingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.E2EIConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SelfDeletingMessagesConfigHandler
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.sync.receiver.handler.AllowedGlobalOperationsHandler
import com.wire.kalium.logic.sync.receiver.handler.AssetAuditLogConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.CellsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.ChatBubblesConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.EnableUserProfileQRCodeConfigHandler
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class FeatureConfigEventReceiverTest {

    @Test
    fun givenFileSharingUpdatedEventWithStatusEnabled_whenProcessingEvent_ThenSetFileSharingStatusToTrue() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSettingFileSharingEnabledSuccessful()
            .withIsFileSharingEnabled(Either.Right(FileSharingStatus(state = FileSharingStatus.Value.Disabled, isStatusChanged = false)))
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            event = arrangement.newFileSharingUpdatedEvent(ConfigsStatusModel(Status.ENABLED)),
            deliveryInfo = TestEvent.liveDeliveryInfo
        )

        verify {
            arrangement.userConfigRepository.setFileSharingStatus(eq(true), eq(true))
        }.wasInvoked(once)
    }

    @Test
    fun givenFileSharingUpdatedEventWithStatusDisabled_whenProcessingEvent_ThenSetFileSharingStatusToFalse() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withIsFileSharingEnabled(Either.Right(FileSharingStatus(state = FileSharingStatus.Value.EnabledAll, isStatusChanged = false)))
            .withSettingFileSharingEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            event = arrangement.newFileSharingUpdatedEvent(ConfigsStatusModel(Status.DISABLED)),
            deliveryInfo = TestEvent.liveDeliveryInfo
        )

        verify {
            arrangement.userConfigRepository.setFileSharingStatus(eq(false), eq(true))
        }.wasInvoked(once)
    }

    @Test
    fun givenFileSharingUpdatedEvent_whenTheNewValueIsSameAsTHeOneStored_ThenIsChangedIsSetToFalse() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withIsFileSharingEnabled(Either.Right(FileSharingStatus(state = FileSharingStatus.Value.Disabled, isStatusChanged = false)))
            .withSettingFileSharingEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            event = arrangement.newFileSharingUpdatedEvent(ConfigsStatusModel(Status.DISABLED)),
            deliveryInfo = TestEvent.liveDeliveryInfo
        )

        verify {
            arrangement.userConfigRepository.setFileSharingStatus(eq(false), eq(false))
        }.wasInvoked(once)
    }

    @Test
    fun givenConferenceCallingEventEnabled_whenProcessingEvent_ThenSetConferenceCallingEnabledToTrueAndSetShouldUseSFTFlag() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSetUseSFTForOneOnOneCallsSuccessful()
            .withSettingConferenceCallingEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            arrangement.newConferenceCallingUpdatedEvent(ConferenceCallingModel(Status.ENABLED, false)),
            TestEvent.liveDeliveryInfo
        )

        verify {
            arrangement.userConfigRepository.setConferenceCallingEnabled(eq(true))
        }.wasInvoked(once)

        verify {
            arrangement.userConfigRepository.setUseSFTForOneOnOneCalls(eq(false))
        }.wasInvoked(once)
    }

    @Test
    fun givenConferenceCallingEventDisabled_whenProcessingEvent_ThenSetConferenceCallingEnabledToFalseOnly() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSetUseSFTForOneOnOneCallsSuccessful()
            .withSettingConferenceCallingEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            event = arrangement.newConferenceCallingUpdatedEvent(ConferenceCallingModel(Status.DISABLED, false)),
            deliveryInfo = TestEvent.liveDeliveryInfo
        )

        verify {
            arrangement.userConfigRepository.setConferenceCallingEnabled(eq(false))
        }.wasInvoked(once)

        verify {
            arrangement.userConfigRepository.setUseSFTForOneOnOneCalls(eq(true))
        }.wasNotInvoked()
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

        featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            arrangement.newSelfDeletingMessagesUpdatedEvent(newSelfDeletingEventModel),
            TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.userConfigRepository.setTeamSettingsSelfDeletionStatus(matches {
                it.hasFeatureChanged == true && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Disabled
            })
        }.wasInvoked(once)
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

            featureConfigEventReceiver.onEvent(
                arrangement.transactionContext,
                arrangement.newSelfDeletingMessagesUpdatedEvent(newSelfDeletingEventModel),
                TestEvent.liveDeliveryInfo
            )

            coVerify {
                arrangement.userConfigRepository.setTeamSettingsSelfDeletionStatus(matches {
                    it.hasFeatureChanged == false && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Enabled
                })
            }.wasInvoked(once)
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

            featureConfigEventReceiver.onEvent(
                arrangement.transactionContext,
                arrangement.newSelfDeletingMessagesUpdatedEvent(newSelfDeletingEventModel),
                TestEvent.liveDeliveryInfo
            )

            coVerify {
                arrangement.userConfigRepository.setTeamSettingsSelfDeletionStatus(matches {
                    it.hasFeatureChanged == true && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Enforced
                            && (it.enforcedSelfDeletionTimer as TeamSelfDeleteTimer.Enforced).enforcedDuration == newEnforcedDuration
                })
            }.wasInvoked(once)
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

            featureConfigEventReceiver.onEvent(
                arrangement.transactionContext,
                arrangement.newSelfDeletingMessagesUpdatedEvent(newSelfDeletingEventModel),
                TestEvent.liveDeliveryInfo
            )

            coVerify {
                arrangement.userConfigRepository.setTeamSettingsSelfDeletionStatus(matches {
                    it.hasFeatureChanged == false && it.enforcedSelfDeletionTimer !is TeamSelfDeleteTimer.Disabled
                })
            }.wasInvoked(once)
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

            featureConfigEventReceiver.onEvent(
                arrangement.transactionContext,
                arrangement.newSelfDeletingMessagesUpdatedEvent(newSelfDeletingEventModel),
                TestEvent.liveDeliveryInfo
            )

            coVerify {
                arrangement.userConfigRepository.setTeamSettingsSelfDeletionStatus(matches {
                    it.hasFeatureChanged == false && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Disabled
                })
            }.wasInvoked(once)
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

        featureConfigEventReceiver.onEvent(
            transactionContext = arrangement.transactionContext,
            event = arrangement.newSelfDeletingMessagesUpdatedEvent(newSelfDeletingEventModel),
            deliveryInfo = TestEvent.liveDeliveryInfo
        )

        coVerify {
            arrangement.userConfigRepository.setTeamSettingsSelfDeletionStatus(
                matches {
                    it.hasFeatureChanged == null && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Disabled
                }
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenUnknownFeatureConfig_whenPrecessing_thenReturnSuccess() = runTest {
        val newUnknownFeatureUpdate = TestEvent.newUnknownFeatureUpdate()
        val (arrangement, handler) = Arrangement()
            .arrange()

        handler.onEvent(
            arrangement.transactionContext,
            newUnknownFeatureUpdate,
            TestEvent.liveDeliveryInfo
        ).shouldSucceed()
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        var kaliumConfigs = KaliumConfigs()

        val userConfigRepository = mock(UserConfigRepository::class)
        val updateSupportedProtocolsAndResolveOneOnOnes = mock(UpdateSupportedProtocolsAndResolveOneOnOnesUseCase::class)

        private val featureConfigEventReceiver: FeatureConfigEventReceiver by lazy {
            FeatureConfigEventReceiverImpl(
                GuestRoomConfigHandler(userConfigRepository, kaliumConfigs),
                FileSharingConfigHandler(userConfigRepository),
                MLSConfigHandler(userConfigRepository, updateSupportedProtocolsAndResolveOneOnOnes, cryptoTransactionProvider),
                MLSMigrationConfigHandler(userConfigRepository, updateSupportedProtocolsAndResolveOneOnOnes, cryptoTransactionProvider),
                ClassifiedDomainsConfigHandler(userConfigRepository),
                ConferenceCallingConfigHandler(userConfigRepository),
                SelfDeletingMessagesConfigHandler(userConfigRepository, kaliumConfigs),
                E2EIConfigHandler(userConfigRepository),
                AppLockConfigHandler(userConfigRepository),
                AllowedGlobalOperationsHandler(userConfigRepository),
                CellsConfigHandler(userConfigRepository),
                ChatBubblesConfigHandler(userConfigRepository),
                EnableUserProfileQRCodeConfigHandler(userConfigRepository),
                AssetAuditLogConfigHandler(userConfigRepository),
            )
        }

        fun withSettingFileSharingEnabledSuccessful() = apply {
            every {
                userConfigRepository.setFileSharingStatus(any(), any())
            }.returns(Either.Right(Unit))
        }

        fun withSettingConferenceCallingEnabledSuccessful() = apply {
            every {
                userConfigRepository.setConferenceCallingEnabled(any())
            }.returns(Either.Right(Unit))
        }

        fun withSetUseSFTForOneOnOneCallsSuccessful() = apply {
            every {
                userConfigRepository.setUseSFTForOneOnOneCalls(any())
            }.returns(Either.Right(Unit))
        }

        fun withIsFileSharingEnabled(result: Either<StorageFailure, FileSharingStatus>) = apply {
            every {
                userConfigRepository.isFileSharingEnabled()
            }.returns(result)
        }

        suspend fun withSelfDeletingMessages(currentSelfDeletingMessagesStatus: TeamSettingsSelfDeletionStatus) = apply {
            coEvery {
                userConfigRepository.getTeamSettingsSelfDeletionStatus()
            }.returns(Either.Right(currentSelfDeletingMessagesStatus))
            coEvery {
                userConfigRepository.setTeamSettingsSelfDeletionStatus(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withStoredTeamSettingsSelfDeletionStatusError() = apply {
            coEvery {
                userConfigRepository.getTeamSettingsSelfDeletionStatus()
            }.returns(Either.Left(StorageFailure.DataNotFound))
            coEvery {
                userConfigRepository.setTeamSettingsSelfDeletionStatus(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withDisabledKaliumConfigFlag() = apply {
            kaliumConfigs = kaliumConfigs.copy(selfDeletingMessages = false)
            coEvery {
                userConfigRepository.setTeamSettingsSelfDeletionStatus(any())
            }.returns(Either.Right(Unit))
        }

        fun newFileSharingUpdatedEvent(
            model: ConfigsStatusModel
        ) = Event.FeatureConfig.FileSharingUpdated("eventId", model)

        fun newConferenceCallingUpdatedEvent(
            model: ConferenceCallingModel
        ) = Event.FeatureConfig.ConferenceCallingUpdated("eventId", model)

        fun newSelfDeletingMessagesUpdatedEvent(
            model: SelfDeletingMessagesModel
        ) = Event.FeatureConfig.SelfDeletingMessagesConfig("eventId", model)

        fun arrange(block: suspend Arrangement.() -> Unit = {}) = run {
            runBlocking { block() }
            this to featureConfigEventReceiver
        }
    }
}
