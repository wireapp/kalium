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
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesConfigModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.SupportedProtocol
import kotlinx.datetime.Instant
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
import com.wire.kalium.logic.sync.receiver.handler.PreventAdminlessGroupsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.CellsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.EnableUserProfileQRCodeConfigHandler
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.shouldSucceed
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setFileSharingStatus(eq(true), eq(true))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setFileSharingStatus(eq(false), eq(true))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setFileSharingStatus(eq(false), eq(false))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setConferenceCallingEnabled(eq(true))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setUseSFTForOneOnOneCalls(eq(false))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setConferenceCallingEnabled(eq(false))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.setUseSFTForOneOnOneCalls(eq(true))
        }
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userConfigRepository.setTeamSettingsSelfDeletionStatus(matches {
                    it.hasFeatureChanged == true && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Disabled
                })
            }
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userConfigRepository.setTeamSettingsSelfDeletionStatus(matches {
                    it.hasFeatureChanged == false && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Enabled
                })
            }
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userConfigRepository.setTeamSettingsSelfDeletionStatus(matches {
                    it.hasFeatureChanged == true && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Enforced
                            && (it.enforcedSelfDeletionTimer as TeamSelfDeleteTimer.Enforced).enforcedDuration == newEnforcedDuration
                })
            }
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userConfigRepository.setTeamSettingsSelfDeletionStatus(matches {
                    it.hasFeatureChanged == false && it.enforcedSelfDeletionTimer !is TeamSelfDeleteTimer.Disabled
                })
            }
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userConfigRepository.setTeamSettingsSelfDeletionStatus(matches {
                    it.hasFeatureChanged == false && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Disabled
                })
            }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setTeamSettingsSelfDeletionStatus(
                matches {
                    it.hasFeatureChanged == null && it.enforcedSelfDeletionTimer is TeamSelfDeleteTimer.Disabled
                }
            )
        }
    }

    @Test
    fun givenMLSUpdatedEvent_whenProcessingEvent_thenTransactionContextIsPassedDirectlyToHandler() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withMLSConfigSetup()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            event = Event.FeatureConfig.MLSUpdated(
                "eventId",
                MLSModel(
                    defaultProtocol = SupportedProtocol.PROTEUS,
                    supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS),
                    status = Status.ENABLED,
                    supportedCipherSuite = null
                )
            ),
            deliveryInfo = TestEvent.liveDeliveryInfo
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes.invoke(eq(arrangement.transactionContext), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.cryptoTransactionProvider.transaction<Any>(any(), any())
        }
    }

    @Test
    fun givenMLSMigrationUpdatedEventWithMigrationEnded_whenProcessingEvent_thenTransactionContextIsPassedDirectlyToHandler() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withMLSMigrationConfigSetup()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.transactionContext,
            event = Event.FeatureConfig.MLSMigrationUpdated(
                "eventId",
                MLSMigrationModel(
                    startTime = Instant.DISTANT_PAST,
                    endTime = Instant.DISTANT_PAST,
                    status = Status.ENABLED
                )
            ),
            deliveryInfo = TestEvent.liveDeliveryInfo
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes.invoke(eq(arrangement.transactionContext), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.cryptoTransactionProvider.transaction<Any>(any(), any())
        }
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

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {

        var kaliumConfigs = KaliumConfigs()

        val userConfigRepository = mock<UserConfigRepository>()
        val updateSupportedProtocolsAndResolveOneOnOnes = mock<UpdateSupportedProtocolsAndResolveOneOnOnesUseCase>()

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
                EnableUserProfileQRCodeConfigHandler(userConfigRepository),
                AssetAuditLogConfigHandler(userConfigRepository),
                PreventAdminlessGroupsConfigHandler(userConfigRepository),
            )
        }

        suspend fun withSettingFileSharingEnabledSuccessful() = apply {
            everySuspend {
                userConfigRepository.setFileSharingStatus(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withSettingConferenceCallingEnabledSuccessful() = apply {
            everySuspend {
                userConfigRepository.setConferenceCallingEnabled(any())
            } returns Either.Right(Unit)
        }

        suspend fun withSetUseSFTForOneOnOneCallsSuccessful() = apply {
            everySuspend {
                userConfigRepository.setUseSFTForOneOnOneCalls(any())
            } returns Either.Right(Unit)
        }

        suspend fun withIsFileSharingEnabled(result: Either<StorageFailure, FileSharingStatus>) = apply {
            everySuspend {
                userConfigRepository.isFileSharingEnabled()
            } returns result
        }

        suspend fun withSelfDeletingMessages(currentSelfDeletingMessagesStatus: TeamSettingsSelfDeletionStatus) = apply {
            everySuspend {
                userConfigRepository.getTeamSettingsSelfDeletionStatus()
            } returns Either.Right(currentSelfDeletingMessagesStatus)
            everySuspend {
                userConfigRepository.setTeamSettingsSelfDeletionStatus(any())
            } returns Either.Right(Unit)
        }

        suspend fun withStoredTeamSettingsSelfDeletionStatusError() = apply {
            everySuspend {
                userConfigRepository.getTeamSettingsSelfDeletionStatus()
            } returns Either.Left(StorageFailure.DataNotFound)
            everySuspend {
                userConfigRepository.setTeamSettingsSelfDeletionStatus(any())
            } returns Either.Right(Unit)
        }

        suspend fun withMLSConfigSetup() = apply {
            everySuspend {
                userConfigRepository.getSupportedProtocols()
            } returns Either.Right(setOf(SupportedProtocol.PROTEUS))
            everySuspend {
                userConfigRepository.setMLSEnabled(any())
            } returns Either.Right(Unit)
            everySuspend {
                userConfigRepository.setDefaultProtocol(any())
            } returns Either.Right(Unit)
            everySuspend {
                userConfigRepository.setSupportedProtocols(any())
            } returns Either.Right(Unit)
            everySuspend {
                updateSupportedProtocolsAndResolveOneOnOnes.invoke(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withMLSMigrationConfigSetup() = apply {
            everySuspend {
                userConfigRepository.setMigrationConfiguration(any())
            } returns Either.Right(Unit)
            everySuspend {
                updateSupportedProtocolsAndResolveOneOnOnes.invoke(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withDisabledKaliumConfigFlag() = apply {
            kaliumConfigs = kaliumConfigs.copy(selfDeletingMessages = false)
            everySuspend {
                userConfigRepository.setTeamSettingsSelfDeletionStatus(any())
            } returns Either.Right(Unit)
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
