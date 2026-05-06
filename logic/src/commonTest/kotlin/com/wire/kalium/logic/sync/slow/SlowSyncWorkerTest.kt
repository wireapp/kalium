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
package com.wire.kalium.logic.sync.slow

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProvider
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.data.sync.SlowSyncStep
import com.wire.kalium.logic.data.user.LegalHoldStatus
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.debug.OptimizeDatabaseResult
import com.wire.kalium.logic.feature.debug.OptimizeDatabaseUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.legalhold.FetchLegalHoldForSelfUserFromRemoteUseCase
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCase
import com.wire.kalium.logic.feature.user.SyncContactsUseCase
import com.wire.kalium.logic.feature.user.SyncSelfUserUseCase
import com.wire.kalium.logic.feature.user.SyncUserPropertiesUseCase
import com.wire.kalium.logic.feature.user.UpdateSelfUserSupportedProtocolsResult
import com.wire.kalium.logic.feature.user.UpdateSelfUserSupportedProtocolsUseCase
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.slow.migration.steps.SyncMigrationStep
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.EventRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.EventRepositoryArrangementMokkeryImpl
import com.wire.kalium.logic.util.stubs.FailureSyncMigration
import com.wire.kalium.logic.util.stubs.MigrationCrashStep
import com.wire.kalium.logic.util.stubs.SuccessSyncMigration
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SlowSyncWorkerTest {

    @Test
    fun givenSuccess_whenPerformingSlowSync_thenRunAllUseCases() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withSyncContactsSuccess()
            .withJoinMLSConversationsSuccess()
            .withResolveOneOnOneConversationsSuccess()
            .withFetchLegalHoldStatusSuccess()
            .arrange()

        worker.slowSyncStepsFlow(successfullyMigration).collect()

        assertUseCases(arrangement, stepsWithNomadDisabled())
    }

    @Test
    fun givenSyncSelfUserFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(SlowSyncStep.MIGRATION, SlowSyncStep.SELF_USER)
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.slowSyncStepsFlow(successfullyMigration).collect {
                assertTrue {
                    it in steps
                }
            }
        }

        assertUseCases(arrangement, steps)
    }

    @Test
    fun givenSyncUserPropertiesFails_whenPerformingSlowSync_thenContinueWithNextSteps() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withSyncUserPropertiesFailure()
            .withSyncFeatureConfigsSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withFetchLegalHoldStatusSuccess()
            .withSyncContactsSuccess()
            .withJoinMLSConversationsSuccess()
            .withResolveOneOnOneConversationsSuccess()
            .arrange()

        worker.slowSyncStepsFlow(successfullyMigration).collect()

        assertUseCases(arrangement, stepsWithNomadDisabled())
    }

    @Test
    fun givenNomadIsEnabled_whenPerformingSlowSync_thenSyncNomadMessagesAfterContacts() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withFetchLegalHoldStatusSuccess()
            .withSyncContactsSuccess()
            .withSyncNomadAllMessagesSuccess()
            .withJoinMLSConversationsSuccess()
            .withResolveOneOnOneConversationsSuccess()
            .withNomadEnabled()
            .arrange()

        val emittedSteps = mutableListOf<SlowSyncStep>()

        worker.slowSyncStepsFlow(successfullyMigration).collect {
            emittedSteps += it
        }

        assertEquals(
            listOf(
                SlowSyncStep.MIGRATION,
                SlowSyncStep.SELF_USER,
                SlowSyncStep.USER_PROPERTIES,
                SlowSyncStep.FEATURE_FLAGS,
                SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS,
                SlowSyncStep.CONVERSATIONS,
                SlowSyncStep.CONNECTIONS,
                SlowSyncStep.SELF_TEAM,
                SlowSyncStep.LEGAL_HOLD,
                SlowSyncStep.CONTACTS,
                SlowSyncStep.NOMAD_MESSAGES,
                SlowSyncStep.JOINING_MLS_CONVERSATIONS,
                SlowSyncStep.RESOLVE_ONE_ON_ONE_PROTOCOLS,
                SlowSyncStep.OPTIMIZE_DB
            ),
            emittedSteps
        )

        assertUseCases(arrangement, SlowSyncStep.entries.toHashSet())
    }

    @Test
    fun givenSyncFeatureConfigsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.MIGRATION,
            SlowSyncStep.SELF_USER,
            SlowSyncStep.USER_PROPERTIES,
            SlowSyncStep.FEATURE_FLAGS
        )
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withSyncFeatureConfigsFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.slowSyncStepsFlow(successfullyMigration).collect {
                assertTrue {
                    it in steps
                }
            }
        }

        assertUseCases(arrangement, steps)
    }

    @Test
    fun givenUpdateSupportedProtocolsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.MIGRATION,
            SlowSyncStep.SELF_USER,
            SlowSyncStep.USER_PROPERTIES,
            SlowSyncStep.FEATURE_FLAGS,
            SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS
        )
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withSyncFeatureConfigsSuccess()
            .withUpdateSupportedProtocolsFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.slowSyncStepsFlow(successfullyMigration).collect {
                assertTrue {
                    it in steps
                }
            }
        }

        assertUseCases(arrangement, steps)
    }

    @Test
    fun givenSyncConversationsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.MIGRATION,
            SlowSyncStep.SELF_USER,
            SlowSyncStep.USER_PROPERTIES,
            SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS,
            SlowSyncStep.FEATURE_FLAGS,
            SlowSyncStep.CONVERSATIONS
        )
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.slowSyncStepsFlow(successfullyMigration).collect {
                assertTrue {
                    it in steps
                }
            }
        }

        assertUseCases(arrangement, steps)
    }

    @Test
    fun givenSyncConnectionsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.MIGRATION,
            SlowSyncStep.SELF_USER,
            SlowSyncStep.USER_PROPERTIES,
            SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS,
            SlowSyncStep.FEATURE_FLAGS,
            SlowSyncStep.CONVERSATIONS,
            SlowSyncStep.CONNECTIONS,
        )
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.slowSyncStepsFlow(successfullyMigration).collect {
                assertTrue {
                    it in steps
                }
            }
        }

        assertUseCases(arrangement, steps)
    }

    @Test
    fun givenSyncSelfTeamFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.MIGRATION,
            SlowSyncStep.SELF_USER,
            SlowSyncStep.USER_PROPERTIES,
            SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS,
            SlowSyncStep.FEATURE_FLAGS,
            SlowSyncStep.CONVERSATIONS,
            SlowSyncStep.CONNECTIONS,
            SlowSyncStep.SELF_TEAM,
        )
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.slowSyncStepsFlow(successfullyMigration).collect {
                assertTrue {
                    it in steps
                }
            }
        }

        assertUseCases(arrangement, steps)
    }

    @Test
    fun givenFetchLegalHoldStatusFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.MIGRATION,
            SlowSyncStep.SELF_USER,
            SlowSyncStep.USER_PROPERTIES,
            SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS,
            SlowSyncStep.FEATURE_FLAGS,
            SlowSyncStep.CONVERSATIONS,
            SlowSyncStep.CONNECTIONS,
            SlowSyncStep.SELF_TEAM,
            SlowSyncStep.LEGAL_HOLD,
        )
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withFetchLegalHoldStatusFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.slowSyncStepsFlow(successfullyMigration).collect {
                assertTrue {
                    it in steps
                }
            }
        }

        assertUseCases(arrangement, steps)
    }

    @Test
    fun givenSyncContactsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.MIGRATION,
            SlowSyncStep.SELF_USER,
            SlowSyncStep.USER_PROPERTIES,
            SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS,
            SlowSyncStep.FEATURE_FLAGS,
            SlowSyncStep.CONVERSATIONS,
            SlowSyncStep.CONNECTIONS,
            SlowSyncStep.SELF_TEAM,
            SlowSyncStep.LEGAL_HOLD,
            SlowSyncStep.CONTACTS,
        )
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withFetchLegalHoldStatusSuccess()
            .withSyncContactsFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.slowSyncStepsFlow(successfullyMigration).collect {
                assertTrue {
                    it in steps
                }
            }
        }

        assertUseCases(arrangement, steps)
    }

    @Test
    fun givenNomadMessagesSyncFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.MIGRATION,
            SlowSyncStep.SELF_USER,
            SlowSyncStep.USER_PROPERTIES,
            SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS,
            SlowSyncStep.FEATURE_FLAGS,
            SlowSyncStep.CONVERSATIONS,
            SlowSyncStep.CONNECTIONS,
            SlowSyncStep.SELF_TEAM,
            SlowSyncStep.LEGAL_HOLD,
            SlowSyncStep.CONTACTS,
            SlowSyncStep.NOMAD_MESSAGES,
        )
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withFetchLegalHoldStatusSuccess()
            .withSyncContactsSuccess()
            .withSyncNomadAllMessagesFailure()
            .withNomadEnabled()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.slowSyncStepsFlow(successfullyMigration).collect {
                assertTrue {
                    it in steps
                }
            }
        }

        assertUseCases(arrangement, steps)
    }

    @Test
    fun givenJoinMLSConversationsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.MIGRATION,
            SlowSyncStep.SELF_USER,
            SlowSyncStep.USER_PROPERTIES,
            SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS,
            SlowSyncStep.FEATURE_FLAGS,
            SlowSyncStep.CONVERSATIONS,
            SlowSyncStep.CONNECTIONS,
            SlowSyncStep.SELF_TEAM,
            SlowSyncStep.LEGAL_HOLD,
            SlowSyncStep.CONTACTS,
            SlowSyncStep.JOINING_MLS_CONVERSATIONS
        )
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withFetchLegalHoldStatusSuccess()
            .withSyncContactsSuccess()
            .withJoinMLSConversationsFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.slowSyncStepsFlow(successfullyMigration).collect {
                assertTrue {
                    it in steps
                }
            }
        }

        assertUseCases(arrangement, steps)
    }

    @Test
    fun givenNoExistingLastProcessedId_whenWorking_thenShouldFetchMostRecentEvent() = runTest {
        val (arrangement, slowSyncWorker) = Arrangement().apply {
            withLastSavedEventIdReturning(Either.Left(StorageFailure.DataNotFound))
            withFetchMostRecentEventReturning(Either.Right("mostRecentEventId"))
        }.withSyncSelfUserFailure()
            .arrange()

        assertFails {
            slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.eventRepository.fetchMostRecentEventId()
        }
    }

    @Test
    fun givenNoExistingLastProcessedId_whenWorkingAndAsyncNotifications_thenShouldNotFetchMostRecentEvent() = runTest {
        val (arrangement, slowSyncWorker) = Arrangement().apply {
            withIsClientAsyncNotificationsCapableReturning(true)
            withLastSavedEventIdReturning(Either.Left(StorageFailure.DataNotFound))
        }.withSyncSelfUserFailure()
            .arrange()

        assertFails {
            slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.eventRepository.fetchMostRecentEventId()
        }
    }

    @Test
    fun givenAlreadyExistingLastProcessedId_whenWorking_thenShouldNotFetchMostRecentEvent() = runTest {
        val (arrangement, slowSyncWorker) = Arrangement().apply {
            withLastSavedEventIdReturning(Either.Right("LastSavedEventId"))
        }.withSyncSelfUserFailure()
            .arrange()

        assertFails {
            slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.eventRepository.fetchMostRecentEventId()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.eventRepository.updateLastSavedEventId(any())
        }
    }

    @Test
    fun givenFetchedEventIdAndSomethingFails_whenWorking_thenShouldNotUpdateLastSavedEventId() = runTest {
        val (arrangement, slowSyncWorker) = Arrangement().apply {
            withLastSavedEventIdReturning(Either.Left(StorageFailure.DataNotFound))
            withFetchMostRecentEventReturning(Either.Right("mostRecentEventId"))
        }.withSyncSelfUserFailure()
            .arrange()

        assertFails {
            slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.eventRepository.updateLastSavedEventId(any())
        }
    }

    @Test
    fun givenFetchedEventIdAndEverythingSucceeds_whenWorking_thenShouldUpdateLastSavedEventId() = runTest {
        val fetchedEventId = "aTestEventId"
        val (arrangement, slowSyncWorker) = Arrangement().apply {
            withLastSavedEventIdReturning(Either.Left(StorageFailure.DataNotFound))
            withFetchMostRecentEventReturning(Either.Right(fetchedEventId))
            withUpdateLastSavedEventIdReturning(Either.Right(Unit))
        }.withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withFetchLegalHoldStatusSuccess()
            .withSyncContactsSuccess()
            .withJoinMLSConversationsSuccess(allowJoinByExternalCommit = true)
            .withResolveOneOnOneConversationsSuccess(allowJoinByExternalCommit = true)
            .withFetchLegalHoldStatusSuccess()
            .arrange()

        slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.eventRepository.updateLastSavedEventId(eq(fetchedEventId))
        }
    }

    @Test
    fun givenAlreadyExistingLastProcessedId_whenJoiningMlsConversations_thenSkipExternalCommitJoins() = runTest {
        val (arrangement, slowSyncWorker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withFetchLegalHoldStatusSuccess()
            .withSyncContactsSuccess()
            .withJoinMLSConversationsSuccess(allowJoinByExternalCommit = false)
            .withResolveOneOnOneConversationsSuccess()
            .arrange()

        slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinMLSConversations.invoke(eq(true), eq(false))
        }
    }

    @Test
    fun givenNoExistingLastProcessedId_whenJoiningMlsConversations_thenAllowExternalCommitJoins() = runTest {
        val fetchedEventId = "aTestEventId"
        val (arrangement, slowSyncWorker) = Arrangement().apply {
            withLastSavedEventIdReturning(Either.Left(StorageFailure.DataNotFound))
            withFetchMostRecentEventReturning(Either.Right(fetchedEventId))
            withUpdateLastSavedEventIdReturning(Either.Right(Unit))
        }
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withFetchLegalHoldStatusSuccess()
            .withSyncContactsSuccess()
            .withJoinMLSConversationsSuccess(allowJoinByExternalCommit = true)
            .withResolveOneOnOneConversationsSuccess(allowJoinByExternalCommit = true)
            .arrange()

        slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinMLSConversations.invoke(eq(true), eq(true))
        }
    }

    @Test
    fun givenAlreadyExistingLastProcessedId_whenResolvingOneOnOnes_thenSkipExternalCommitJoins() = runTest {
        val (arrangement, slowSyncWorker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withFetchLegalHoldStatusSuccess()
            .withSyncContactsSuccess()
            .withJoinMLSConversationsSuccess(allowJoinByExternalCommit = false)
            .withResolveOneOnOneConversationsSuccess(allowJoinByExternalCommit = false)
            .arrange()

        slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.oneOnOneResolver.resolveAllOneOnOneConversations(any(), eq(false), eq(false))
        }
    }

    @Test
    fun givenNoExistingLastProcessedId_whenResolvingOneOnOnes_thenAllowExternalCommitJoins() = runTest {
        val fetchedEventId = "aTestEventId"
        val (arrangement, slowSyncWorker) = Arrangement().apply {
            withLastSavedEventIdReturning(Either.Left(StorageFailure.DataNotFound))
            withFetchMostRecentEventReturning(Either.Right(fetchedEventId))
            withUpdateLastSavedEventIdReturning(Either.Right(Unit))
        }
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withFetchLegalHoldStatusSuccess()
            .withSyncContactsSuccess()
            .withJoinMLSConversationsSuccess(allowJoinByExternalCommit = true)
            .withResolveOneOnOneConversationsSuccess(allowJoinByExternalCommit = true)
            .arrange()

        slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.oneOnOneResolver.resolveAllOneOnOneConversations(any(), eq(false), eq(true))
        }
    }

    @Test
    fun givenMigrationFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(SlowSyncStep.MIGRATION)
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.slowSyncStepsFlow(failedMigration).collect {
                assertTrue {
                    it in steps
                }
            }
        }

        verifySuspend(VerifyMode.not) {
            arrangement.syncSelfUser.invoke()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.syncUserProperties.invoke()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.syncFeatureConfigs.invoke()
        }
    }

    private suspend fun assertUseCases(arrangement: Arrangement, steps: HashSet<SlowSyncStep>) {
        verifySuspend(VerifyMode.exactly(if (steps.contains(SlowSyncStep.SELF_USER)) 1 else 0)) {
            arrangement.syncSelfUser.invoke()
        }

        verifySuspend(VerifyMode.exactly(if (steps.contains(SlowSyncStep.USER_PROPERTIES)) 1 else 0)) {
            arrangement.syncUserProperties.invoke()
        }

        verifySuspend(VerifyMode.exactly(if (steps.contains(SlowSyncStep.FEATURE_FLAGS)) 1 else 0)) {
            arrangement.syncFeatureConfigs.invoke()
        }

        verifySuspend(VerifyMode.exactly(if (steps.contains(SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS)) 1 else 0)) {
            arrangement.updateSupportedProtocols.invoke()
        }

        verifySuspend(VerifyMode.exactly(if (steps.contains(SlowSyncStep.CONVERSATIONS)) 1 else 0)) {
            arrangement.syncConversations.invoke()
        }

        verifySuspend(VerifyMode.exactly(if (steps.contains(SlowSyncStep.CONNECTIONS)) 1 else 0)) {
            arrangement.syncConnections.invoke()
        }

        verifySuspend(VerifyMode.exactly(if (steps.contains(SlowSyncStep.SELF_TEAM)) 1 else 0)) {
            arrangement.syncSelfTeam.invoke()
        }

        verifySuspend(VerifyMode.exactly(if (steps.contains(SlowSyncStep.CONTACTS)) 1 else 0)) {
            arrangement.syncContacts.invoke()
        }

        assertEquals(
            if (steps.contains(SlowSyncStep.NOMAD_MESSAGES)) 1 else 0,
            arrangement.syncNomadMessagesDuringSlowSync.invocations
        )

        verifySuspend(VerifyMode.exactly(if (steps.contains(SlowSyncStep.OPTIMIZE_DB)) 1 else 0)) {
            arrangement.optimizeDatabase.invoke()
        }

        verifySuspend(VerifyMode.exactly(if (steps.contains(SlowSyncStep.JOINING_MLS_CONVERSATIONS)) 1 else 0)) {
            arrangement.joinMLSConversations.invoke(any(), any())
        }

        verifySuspend(VerifyMode.exactly(if (steps.contains(SlowSyncStep.LEGAL_HOLD)) 1 else 0)) {
            arrangement.fetchLegalHoldForSelfUserFromRemoteUseCase.invoke()
        }
    }

    private class Arrangement : EventRepositoryArrangement by EventRepositoryArrangementMokkeryImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val syncSelfUser: SyncSelfUserUseCase = mock()
        val syncUserProperties: SyncUserPropertiesUseCase = mock()
        val syncFeatureConfigs: SyncFeatureConfigsUseCase = mock()
        val syncConversations: SyncConversationsUseCase = mock()
        val syncConnections: SyncConnectionsUseCase = mock()
        val syncSelfTeam: SyncSelfTeamUseCase = mock()
        val syncContacts: SyncContactsUseCase = mock()
        val joinMLSConversations: JoinExistingMLSConversationsUseCase = mock()
        val updateSupportedProtocols: UpdateSelfUserSupportedProtocolsUseCase = mock()
        val oneOnOneResolver: OneOnOneResolver = mock()
        val fetchLegalHoldForSelfUserFromRemoteUseCase: FetchLegalHoldForSelfUserFromRemoteUseCase = mock()
        val isClientAsyncNotificationsCapableProvider: IsClientAsyncNotificationsCapableProvider = mock()
        val syncNomadMessagesDuringSlowSync = FakeSyncNomadMessagesDuringSlowSyncUseCase()
        val optimizeDatabase: OptimizeDatabaseUseCase = mock()

        init {
            runBlocking {
                withLastSavedEventIdReturning(Either.Right("LastSavedEventId"))
                withIsClientAsyncNotificationsCapableReturning(false)
                withTransactionReturning(Either.Right(Unit))
                withSyncUserPropertiesSuccess()
                withOptimizeDatabaseSuccess()
            }
        }

        fun arrange() = this to SlowSyncWorkerImpl(
            eventRepository = eventRepository,
            syncSelfUser = syncSelfUser,
            syncUserProperties = syncUserProperties,
            syncFeatureConfigs = syncFeatureConfigs,
            syncConversations = syncConversations,
            syncConnections = syncConnections,
            syncSelfTeam = syncSelfTeam,
            syncContacts = syncContacts,
            joinMLSConversations = joinMLSConversations,
            updateSupportedProtocols = updateSupportedProtocols,
            fetchLegalHoldForSelfUserFromRemoteUseCase = fetchLegalHoldForSelfUserFromRemoteUseCase,
            oneOnOneResolver = oneOnOneResolver,
            isClientAsyncNotificationsCapableProvider = isClientAsyncNotificationsCapableProvider,
            transactionProvider = cryptoTransactionProvider,
            syncNomadMessagesDuringSlowSync = syncNomadMessagesDuringSlowSync,
            optimizer = optimizeDatabase
        )

        fun withSyncSelfUserFailure() = apply {
            everySuspend {
                syncSelfUser.invoke()
            } returns failure
        }

        fun withSyncSelfUserSuccess() = apply {
            everySuspend {
                syncSelfUser.invoke()
            } returns success
        }

        fun withSyncUserPropertiesFailure() = apply {
            everySuspend {
                syncUserProperties.invoke()
            } returns failure
        }

        fun withSyncUserPropertiesSuccess() = apply {
            everySuspend {
                syncUserProperties.invoke()
            } returns success
        }

        fun withSyncFeatureConfigsFailure() = apply {
            everySuspend {
                syncFeatureConfigs.invoke()
            } returns failure
        }

        fun withSyncFeatureConfigsSuccess() = apply {
            everySuspend {
                syncFeatureConfigs.invoke()
            } returns success
        }

        fun withUpdateSupportedProtocolsSuccess() = apply {
            everySuspend {
                updateSupportedProtocols.invoke()
            } returns UpdateSelfUserSupportedProtocolsResult.Updated
        }

        fun withUpdateSupportedProtocolsFailure() = apply {
            everySuspend {
                updateSupportedProtocols.invoke()
            } returns UpdateSelfUserSupportedProtocolsResult.Failure(failure.value)
        }

        fun withSyncConversationsFailure() = apply {
            everySuspend {
                syncConversations.invoke()
            } returns failure
        }

        fun withSyncConversationsSuccess() = apply {
            everySuspend {
                syncConversations.invoke()
            } returns success
        }

        fun withSyncConnectionsFailure() = apply {
            everySuspend {
                syncConnections.invoke()
            } returns failure
        }

        fun withSyncConnectionsSuccess() = apply {
            everySuspend {
                syncConnections.invoke()
            } returns success
        }

        fun withSyncSelfTeamFailure() = apply {
            everySuspend {
                syncSelfTeam.invoke()
            } returns failure
        }

        fun withSyncSelfTeamSuccess() = apply {
            everySuspend {
                syncSelfTeam.invoke()
            } returns success
        }

        fun withSyncContactsFailure() = apply {
            everySuspend {
                syncContacts.invoke()
            } returns failure
        }

        fun withSyncContactsSuccess() = apply {
            everySuspend {
                syncContacts.invoke()
            } returns success
        }

        fun withJoinMLSConversationsFailure(
            keepRetryingOnFailure: Boolean = true,
            allowJoinByExternalCommit: Boolean = false,
        ) = apply {
            everySuspend {
                joinMLSConversations.invoke(eq(keepRetryingOnFailure), eq(allowJoinByExternalCommit))
            } returns failure
        }

        fun withJoinMLSConversationsSuccess(
            keepRetryingOnFailure: Boolean = true,
            allowJoinByExternalCommit: Boolean = false,
        ) = apply {
            everySuspend {
                joinMLSConversations.invoke(eq(keepRetryingOnFailure), eq(allowJoinByExternalCommit))
            } returns success
        }

        fun withFetchLegalHoldStatusFailure() = apply {
            everySuspend {
                fetchLegalHoldForSelfUserFromRemoteUseCase.invoke()
            } returns failure
        }

        fun withFetchLegalHoldStatusSuccess(status: LegalHoldStatus = LegalHoldStatus.NO_CONSENT) = apply {
            everySuspend {
                fetchLegalHoldForSelfUserFromRemoteUseCase.invoke()
            } returns Either.Right(status)
        }

        fun withResolveOneOnOneConversationsSuccess(
            allowJoinByExternalCommit: Boolean = false,
        ) = apply {
            everySuspend {
                oneOnOneResolver.resolveAllOneOnOneConversations(any(), any(), eq(allowJoinByExternalCommit))
            } returns success
        }

        fun withOptimizeDatabaseSuccess() = apply {
            everySuspend {
                optimizeDatabase.invoke()
            } returns OptimizeDatabaseResult.Success
        }

        fun withIsClientAsyncNotificationsCapableReturning(value: Boolean) = apply {
            every {
                isClientAsyncNotificationsCapableProvider.isClientAsyncNotificationsCapable()
            } returns value
        }

        fun withNomadEnabled() = apply {
            syncNomadMessagesDuringSlowSync.enabled = true
        }

        fun withSyncNomadAllMessagesSuccess() = apply {
            syncNomadMessagesDuringSlowSync.result = success
        }

        fun withSyncNomadAllMessagesFailure() = apply {
            syncNomadMessagesDuringSlowSync.result = failure
        }

        class FakeSyncNomadMessagesDuringSlowSyncUseCase : SyncNomadMessagesDuringSlowSyncUseCase {
            var enabled: Boolean = false
            var invocations: Int = 0
            var result: Either<CoreFailure, Unit> = Either.Right(Unit)

            override fun isEnabled(): Boolean = enabled

            override suspend fun invoke(): Either<CoreFailure, Unit> {
                invocations += 1
                return result
            }
        }
    }

    private companion object {
        val failure = Either.Left(CoreFailure.Unknown(null))
        val success = Either.Right(Unit)

        val successfullyMigration: List<SuccessSyncMigration> = listOf(
            SuccessSyncMigration(1),
            SuccessSyncMigration(2),
            SuccessSyncMigration(3),
        )

        val failedMigration: List<SyncMigrationStep> = listOf(
            SuccessSyncMigration(1),
            FailureSyncMigration(2),
            MigrationCrashStep(3, "this step should never be executed"),
        )

        fun stepsWithNomadDisabled(): HashSet<SlowSyncStep> =
            SlowSyncStep.entries.filterNot { it == SlowSyncStep.NOMAD_MESSAGES }.toHashSet()
    }
}
