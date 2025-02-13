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
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.data.sync.SlowSyncStep
import com.wire.kalium.logic.data.user.LegalHoldStatus
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.legalhold.FetchLegalHoldForSelfUserFromRemoteUseCase
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCase
import com.wire.kalium.logic.feature.user.SyncContactsUseCase
import com.wire.kalium.logic.feature.user.SyncSelfUserUseCase
import com.wire.kalium.logic.feature.user.UpdateSelfUserSupportedProtocolsUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.slow.migration.steps.SyncMigrationStep
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.repository.EventRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.EventRepositoryArrangementImpl
import com.wire.kalium.logic.util.stubs.FailureSyncMigration
import com.wire.kalium.logic.util.stubs.MigrationCrashStep
import com.wire.kalium.logic.util.stubs.SuccessSyncMigration
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import io.mockative.times
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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

        assertUseCases(arrangement, SlowSyncStep.values().toHashSet())
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
    fun givenSyncFeatureConfigsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.MIGRATION,
            SlowSyncStep.SELF_USER,
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
    fun givenJoinMLSConversationsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.MIGRATION,
            SlowSyncStep.SELF_USER,
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
            withLastProcessedEventIdReturning(Either.Left(StorageFailure.DataNotFound))
            withFetchMostRecentEventReturning(Either.Right("mostRecentEventId"))
        }.withSyncSelfUserFailure()
            .arrange()

        assertFails {
            slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()
        }

        coVerify {
            arrangement.eventRepository.fetchMostRecentEventId()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAlreadyExistingLastProcessedId_whenWorking_thenShouldNotFetchMostRecentEvent() = runTest {
        val (arrangement, slowSyncWorker) = Arrangement().apply {
            withLastProcessedEventIdReturning(Either.Right("lastProcessedEventId"))
        }.withSyncSelfUserFailure()
            .arrange()

        assertFails {
            slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()
        }

        coVerify {
            arrangement.eventRepository.fetchMostRecentEventId()
        }.wasNotInvoked()

        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenFetchedEventIdAndSomethingFails_whenWorking_thenShouldNotUpdateLastProcessedEventId() = runTest {
        val (arrangement, slowSyncWorker) = Arrangement().apply {
            withLastProcessedEventIdReturning(Either.Left(StorageFailure.DataNotFound))
            withFetchMostRecentEventReturning(Either.Right("mostRecentEventId"))
        }.withSyncSelfUserFailure()
            .arrange()

        assertFails {
            slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()
        }

        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenFetchedEventIdAndEverythingSucceeds_whenWorking_thenShouldUpdateLastProcessedEventId() = runTest {
        val fetchedEventId = "aTestEventId"
        val (arrangement, slowSyncWorker) = Arrangement().apply {
            withLastProcessedEventIdReturning(Either.Left(StorageFailure.DataNotFound))
            withFetchMostRecentEventReturning(Either.Right(fetchedEventId))
            withUpdateLastProcessedEventIdReturning(Either.Right(Unit))
        }.withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withFetchLegalHoldStatusSuccess()
            .withSyncContactsSuccess()
            .withJoinMLSConversationsSuccess()
            .withResolveOneOnOneConversationsSuccess()
            .withFetchLegalHoldStatusSuccess()
            .arrange()

        slowSyncWorker.slowSyncStepsFlow(successfullyMigration).collect()

        coVerify {
            arrangement.eventRepository.updateLastProcessedEventId(eq(fetchedEventId))
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.syncSelfUser.invoke()
        }.wasNotInvoked()

        coVerify {
            arrangement.syncFeatureConfigs.invoke()
        }.wasNotInvoked()
    }

    private suspend fun assertUseCases(arrangement: Arrangement, steps: HashSet<SlowSyncStep>) {
        coVerify {
            arrangement.syncSelfUser.invoke()
        }.wasInvoked(exactly = if (steps.contains(SlowSyncStep.SELF_USER)) once else 0.times)

        coVerify {
            arrangement.syncFeatureConfigs.invoke()
        }.wasInvoked(exactly = if (steps.contains(SlowSyncStep.FEATURE_FLAGS)) once else 0.times)

        coVerify {
            arrangement.updateSupportedProtocols.invoke()
        }.wasInvoked(exactly = if (steps.contains(SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS)) once else 0.times)

        coVerify {
            arrangement.syncConversations.invoke()
        }.wasInvoked(exactly = if (steps.contains(SlowSyncStep.CONVERSATIONS)) once else 0.times)

        coVerify {
            arrangement.syncConnections.invoke()
        }.wasInvoked(exactly = if (steps.contains(SlowSyncStep.CONNECTIONS)) once else 0.times)

        coVerify {
            arrangement.syncSelfTeam.invoke()
        }.wasInvoked(exactly = if (steps.contains(SlowSyncStep.SELF_TEAM)) once else 0.times)

        coVerify {
            arrangement.syncContacts.invoke()
        }.wasInvoked(exactly = if (steps.contains(SlowSyncStep.CONTACTS)) once else 0.times)

        coVerify {
            arrangement.joinMLSConversations.invoke(any())
        }.wasInvoked(exactly = if (steps.contains(SlowSyncStep.JOINING_MLS_CONVERSATIONS)) once else 0.times)

        coVerify {
            arrangement.fetchLegalHoldForSelfUserFromRemoteUseCase.invoke()
        }.wasInvoked(exactly = if (steps.contains(SlowSyncStep.LEGAL_HOLD)) once else 0.times)
    }

    private class Arrangement : EventRepositoryArrangement by EventRepositoryArrangementImpl() {

        @Mock
        val syncSelfUser: SyncSelfUserUseCase = mock(SyncSelfUserUseCase::class)

        @Mock
        val syncFeatureConfigs: SyncFeatureConfigsUseCase = mock(SyncFeatureConfigsUseCase::class)

        @Mock
        val syncConversations: SyncConversationsUseCase = mock(SyncConversationsUseCase::class)

        @Mock
        val syncConnections: SyncConnectionsUseCase = mock(SyncConnectionsUseCase::class)

        @Mock
        val syncSelfTeam: SyncSelfTeamUseCase = mock(SyncSelfTeamUseCase::class)

        @Mock
        val syncContacts: SyncContactsUseCase = mock(SyncContactsUseCase::class)

        @Mock
        val joinMLSConversations: JoinExistingMLSConversationsUseCase = mock(JoinExistingMLSConversationsUseCase::class)

        @Mock
        val updateSupportedProtocols: UpdateSelfUserSupportedProtocolsUseCase = mock(UpdateSelfUserSupportedProtocolsUseCase::class)

        @Mock
        val oneOnOneResolver: OneOnOneResolver = mock(OneOnOneResolver::class)

        @Mock
        val fetchLegalHoldForSelfUserFromRemoteUseCase = mock(FetchLegalHoldForSelfUserFromRemoteUseCase::class)

        init {
            runBlocking {
                withLastProcessedEventIdReturning(Either.Right("lastProcessedEventId"))
            }
        }

        fun arrange() = this to SlowSyncWorkerImpl(
            eventRepository = eventRepository,
            syncSelfUser = syncSelfUser,
            syncFeatureConfigs = syncFeatureConfigs,
            syncConversations = syncConversations,
            syncConnections = syncConnections,
            syncSelfTeam = syncSelfTeam,
            syncContacts = syncContacts,
            joinMLSConversations = joinMLSConversations,
            updateSupportedProtocols = updateSupportedProtocols,
            fetchLegalHoldForSelfUserFromRemoteUseCase = fetchLegalHoldForSelfUserFromRemoteUseCase,
            oneOnOneResolver = oneOnOneResolver
        )

        suspend fun withSyncSelfUserFailure() = apply {
            coEvery {
                syncSelfUser.invoke()
            }.returns(failure)
        }

        suspend fun withSyncSelfUserSuccess() = apply {
            coEvery {
                syncSelfUser.invoke()
            }.returns(success)
        }

        suspend fun withSyncFeatureConfigsFailure() = apply {
            coEvery {
                syncFeatureConfigs.invoke()
            }.returns(failure)
        }

        suspend fun withSyncFeatureConfigsSuccess() = apply {
            coEvery {
                syncFeatureConfigs.invoke()
            }.returns(success)
        }

        suspend fun withUpdateSupportedProtocolsSuccess() = apply {
            coEvery {
                updateSupportedProtocols.invoke()
            }.returns(Either.Right(true))
        }

        suspend fun withUpdateSupportedProtocolsFailure() = apply {
            coEvery {
                updateSupportedProtocols.invoke()
            }.returns(failure)
        }

        suspend fun withSyncConversationsFailure() = apply {
            coEvery {
                syncConversations.invoke()
            }.returns(failure)
        }

        suspend fun withSyncConversationsSuccess() = apply {
            coEvery {
                syncConversations.invoke()
            }.returns(success)
        }

        suspend fun withSyncConnectionsFailure() = apply {
            coEvery {
                syncConnections.invoke()
            }.returns(failure)
        }

        suspend fun withSyncConnectionsSuccess() = apply {
            coEvery {
                syncConnections.invoke()
            }.returns(success)
        }

        suspend fun withSyncSelfTeamFailure() = apply {
            coEvery {
                syncSelfTeam.invoke()
            }.returns(failure)
        }

        suspend fun withSyncSelfTeamSuccess() = apply {
            coEvery {
                syncSelfTeam.invoke()
            }.returns(success)
        }

        suspend fun withSyncContactsFailure() = apply {
            coEvery {
                syncContacts.invoke()
            }.returns(failure)
        }

        suspend fun withSyncContactsSuccess() = apply {
            coEvery {
                syncContacts.invoke()
            }.returns(success)
        }

        suspend fun withJoinMLSConversationsFailure(keepRetryingOnFailure: Boolean = true) = apply {
            coEvery {
                joinMLSConversations.invoke(eq(keepRetryingOnFailure))
            }.returns(failure)
        }

        suspend fun withJoinMLSConversationsSuccess(keepRetryingOnFailure: Boolean = true) = apply {
            coEvery {
                joinMLSConversations.invoke(eq(keepRetryingOnFailure))
            }.returns(success)
        }

        suspend fun withFetchLegalHoldStatusFailure() = apply {
            coEvery {
                fetchLegalHoldForSelfUserFromRemoteUseCase.invoke()
            }.returns(failure)
        }

        suspend fun withFetchLegalHoldStatusSuccess(status: LegalHoldStatus = LegalHoldStatus.NO_CONSENT) = apply {
            coEvery {
                fetchLegalHoldForSelfUserFromRemoteUseCase.invoke()
            }.returns(Either.Right(status))
        }

        suspend fun withResolveOneOnOneConversationsSuccess() = apply {
            coEvery {
                oneOnOneResolver.resolveAllOneOnOneConversations(any())
            }.returns(success)
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
    }
}
