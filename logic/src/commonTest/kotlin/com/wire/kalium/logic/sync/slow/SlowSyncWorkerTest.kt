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
package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.sync.SlowSyncStep
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCase
import com.wire.kalium.logic.feature.user.SyncContactsUseCase
import com.wire.kalium.logic.feature.user.SyncSelfUserUseCase
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
            .arrange()

        worker.performSlowSyncSteps().collect()

        assertAllUseCasesSuccessfulRun(arrangement)
    }

    @Test
    fun givenSyncSelfUserFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(SlowSyncStep.SELF_USER)
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.performSlowSyncSteps().collect {
                assertTrue {
                    it in steps
                }
            }
        }

        verify(arrangement.syncSelfUser)
            .suspendFunction(arrangement.syncSelfUser::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncFeatureConfigs)
            .suspendFunction(arrangement.syncFeatureConfigs::invoke)
            .wasNotInvoked()
    }

    @Test
    fun givenSyncFeatureConfigsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(SlowSyncStep.SELF_USER, SlowSyncStep.FEATURE_FLAGS)
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withSyncFeatureConfigsFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.performSlowSyncSteps().collect {
                assertTrue {
                    it in steps
                }
            }
        }

        verify(arrangement.syncSelfUser)
            .suspendFunction(arrangement.syncSelfUser::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncFeatureConfigs)
            .suspendFunction(arrangement.syncFeatureConfigs::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncConversations)
            .suspendFunction(arrangement.syncConversations::invoke)
            .wasNotInvoked()
    }

    @Test
    fun givenUpdateSupportedProtocolsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(SlowSyncStep.SELF_USER, SlowSyncStep.FEATURE_FLAGS, SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS)
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withSyncFeatureConfigsSuccess()
            .withUpdateSupportedProtocolsFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.performSlowSyncSteps().collect {
                assertTrue {
                    it in steps
                }
            }
        }

        verify(arrangement.syncSelfUser)
            .suspendFunction(arrangement.syncSelfUser::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncFeatureConfigs)
            .suspendFunction(arrangement.syncFeatureConfigs::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncConversations)
            .suspendFunction(arrangement.syncConversations::invoke)
            .wasNotInvoked()
    }

    @Test
    fun givenSyncConversationsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
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
            worker.performSlowSyncSteps().collect {
                assertTrue {
                    it in steps
                }
            }
        }

        verify(arrangement.syncSelfUser)
            .suspendFunction(arrangement.syncSelfUser::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncFeatureConfigs)
            .suspendFunction(arrangement.syncFeatureConfigs::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.updateSupportedProtocols)
            .suspendFunction(arrangement.updateSupportedProtocols::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncConversations)
            .suspendFunction(arrangement.syncConversations::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncConnections)
            .suspendFunction(arrangement.syncConnections::invoke)
            .wasNotInvoked()
    }

    @Test
    fun givenSyncConnectionsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
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
            worker.performSlowSyncSteps().collect {
                assertTrue {
                    it in steps
                }
            }
        }

        verify(arrangement.syncSelfUser)
            .suspendFunction(arrangement.syncSelfUser::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncFeatureConfigs)
            .suspendFunction(arrangement.syncFeatureConfigs::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.updateSupportedProtocols)
            .suspendFunction(arrangement.updateSupportedProtocols::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncConversations)
            .suspendFunction(arrangement.syncConversations::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncConnections)
            .suspendFunction(arrangement.syncConnections::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncSelfTeam)
            .suspendFunction(arrangement.syncSelfTeam::invoke)
            .wasNotInvoked()
    }

    @Test
    fun givenSyncSelfTeamFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
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
            worker.performSlowSyncSteps().collect {
                assertTrue {
                    it in steps
                }
            }
        }

        verify(arrangement.syncSelfUser)
            .suspendFunction(arrangement.syncSelfUser::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncFeatureConfigs)
            .suspendFunction(arrangement.syncFeatureConfigs::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.updateSupportedProtocols)
            .suspendFunction(arrangement.updateSupportedProtocols::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncConversations)
            .suspendFunction(arrangement.syncConversations::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncConnections)
            .suspendFunction(arrangement.syncConnections::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncSelfTeam)
            .suspendFunction(arrangement.syncSelfTeam::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncContacts)
            .suspendFunction(arrangement.syncContacts::invoke)
            .wasNotInvoked()
    }

    @Test
    fun givenSyncContactsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.SELF_USER,
            SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS,
            SlowSyncStep.FEATURE_FLAGS,
            SlowSyncStep.CONVERSATIONS,
            SlowSyncStep.CONNECTIONS,
            SlowSyncStep.SELF_TEAM,
            SlowSyncStep.CONTACTS,
        )
        val (arrangement, worker) = Arrangement()
            .withSyncSelfUserSuccess()
            .withUpdateSupportedProtocolsSuccess()
            .withSyncFeatureConfigsSuccess()
            .withSyncConversationsSuccess()
            .withSyncConnectionsSuccess()
            .withSyncSelfTeamSuccess()
            .withSyncContactsFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.performSlowSyncSteps().collect {
                assertTrue {
                    it in steps
                }
            }
        }

        verify(arrangement.syncSelfUser)
            .suspendFunction(arrangement.syncSelfUser::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncFeatureConfigs)
            .suspendFunction(arrangement.syncFeatureConfigs::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.updateSupportedProtocols)
            .suspendFunction(arrangement.updateSupportedProtocols::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncConversations)
            .suspendFunction(arrangement.syncConversations::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncConnections)
            .suspendFunction(arrangement.syncConnections::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncSelfTeam)
            .suspendFunction(arrangement.syncSelfTeam::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncContacts)
            .suspendFunction(arrangement.syncContacts::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.joinMLSConversations)
            .suspendFunction(arrangement.joinMLSConversations::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenJoinMLSConversationsFails_whenPerformingSlowSync_thenThrowSyncException() = runTest(TestKaliumDispatcher.default) {
        val steps = hashSetOf(
            SlowSyncStep.SELF_USER,
            SlowSyncStep.UPDATE_SUPPORTED_PROTOCOLS,
            SlowSyncStep.FEATURE_FLAGS,
            SlowSyncStep.CONVERSATIONS,
            SlowSyncStep.CONNECTIONS,
            SlowSyncStep.SELF_TEAM,
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
            .withSyncContactsSuccess()
            .withJoinMLSConversationsFailure()
            .arrange()

        assertFailsWith<KaliumSyncException> {
            worker.performSlowSyncSteps().collect {
                assertTrue {
                    it in steps
                }
            }
        }

        assertAllUseCasesSuccessfulRun(arrangement)
    }

    private fun assertAllUseCasesSuccessfulRun(arrangement: Arrangement) {
        verify(arrangement.syncSelfUser)
            .suspendFunction(arrangement.syncSelfUser::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncFeatureConfigs)
            .suspendFunction(arrangement.syncFeatureConfigs::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.updateSupportedProtocols)
            .suspendFunction(arrangement.updateSupportedProtocols::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncConversations)
            .suspendFunction(arrangement.syncConversations::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncConnections)
            .suspendFunction(arrangement.syncConnections::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncSelfTeam)
            .suspendFunction(arrangement.syncSelfTeam::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncContacts)
            .suspendFunction(arrangement.syncContacts::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.joinMLSConversations)
            .suspendFunction(arrangement.joinMLSConversations::invoke)
            .with(any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

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
        val updateSupportedProtocols: UpdateSupportedProtocolsUseCase = mock(UpdateSupportedProtocolsUseCase::class)

        fun arrange() = this to SlowSyncWorkerImpl(
            syncSelfUser = syncSelfUser,
            syncFeatureConfigs = syncFeatureConfigs,
            syncConversations = syncConversations,
            syncConnections = syncConnections,
            syncSelfTeam = syncSelfTeam,
            syncContacts = syncContacts,
            joinMLSConversations = joinMLSConversations,
            updateSupportedProtocols = updateSupportedProtocols
        )

        fun withSyncSelfUserFailure() = apply {
            given(syncSelfUser)
                .suspendFunction(syncSelfUser::invoke)
                .whenInvoked()
                .thenReturn(failure)
        }

        fun withSyncSelfUserSuccess() = apply {
            given(syncSelfUser)
                .suspendFunction(syncSelfUser::invoke)
                .whenInvoked()
                .thenReturn(success)
        }

        fun withSyncFeatureConfigsFailure() = apply {
            given(syncFeatureConfigs)
                .suspendFunction(syncFeatureConfigs::invoke)
                .whenInvoked()
                .thenReturn(failure)
        }

        fun withSyncFeatureConfigsSuccess() = apply {
            given(syncFeatureConfigs)
                .suspendFunction(syncFeatureConfigs::invoke)
                .whenInvoked()
                .thenReturn(success)
        }

        fun withUpdateSupportedProtocolsSuccess() = apply {
            given(updateSupportedProtocols)
                .suspendFunction(updateSupportedProtocols::invoke)
                .whenInvoked()
                .thenReturn(success)
        }

        fun withUpdateSupportedProtocolsFailure() = apply {
            given(updateSupportedProtocols)
                .suspendFunction(updateSupportedProtocols::invoke)
                .whenInvoked()
                .thenReturn(failure)
        }

        fun withSyncConversationsFailure() = apply {
            given(syncConversations)
                .suspendFunction(syncConversations::invoke)
                .whenInvoked()
                .thenReturn(failure)
        }

        fun withSyncConversationsSuccess() = apply {
            given(syncConversations)
                .suspendFunction(syncConversations::invoke)
                .whenInvoked()
                .thenReturn(success)
        }

        fun withSyncConnectionsFailure() = apply {
            given(syncConnections)
                .suspendFunction(syncConnections::invoke)
                .whenInvoked()
                .thenReturn(failure)
        }

        fun withSyncConnectionsSuccess() = apply {
            given(syncConnections)
                .suspendFunction(syncConnections::invoke)
                .whenInvoked()
                .thenReturn(success)
        }

        fun withSyncSelfTeamFailure() = apply {
            given(syncSelfTeam)
                .suspendFunction(syncConnections::invoke)
                .whenInvoked()
                .thenReturn(failure)
        }

        fun withSyncSelfTeamSuccess() = apply {
            given(syncSelfTeam)
                .suspendFunction(syncConnections::invoke)
                .whenInvoked()
                .thenReturn(success)
        }

        fun withSyncContactsFailure() = apply {
            given(syncContacts)
                .suspendFunction(syncContacts::invoke)
                .whenInvoked()
                .thenReturn(failure)
        }

        fun withSyncContactsSuccess() = apply {
            given(syncContacts)
                .suspendFunction(syncContacts::invoke)
                .whenInvoked()
                .thenReturn(success)
        }

        fun withJoinMLSConversationsFailure(keepRetryingOnFailure: Boolean = true) = apply {
            given(joinMLSConversations)
                .suspendFunction(joinMLSConversations::invoke)
                .whenInvokedWith(eq(keepRetryingOnFailure))
                .thenReturn(failure)
        }

        fun withJoinMLSConversationsSuccess(keepRetryingOnFailure: Boolean = true) = apply {
            given(joinMLSConversations)
                .suspendFunction(joinMLSConversations::invoke)
                .whenInvokedWith(eq(keepRetryingOnFailure))
                .thenReturn(success)
        }
    }

    companion object {
        val failure = Either.Left(CoreFailure.Unknown(null))
        val success = Either.Right(Unit)
    }
}
