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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MLSConversationsRecoveryManagerTests {
    @Test
    fun givenMLSNeedsRecoveryTrue_whenObservingAndSyncFinishes_MLSNeedRecoveryKeyGetsUpdated() =
        runTest {
            val (arrangement, mlsConversationsRecoveryManager) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withRecoverMLSConversationsResult(RecoverMLSConversationsResult.Success)
                .withMLSNeedsRecoveryReturn(true)
                .withIncrementalSyncState(IncrementalSyncStatus.Live)
                .arrange()
            mlsConversationsRecoveryManager.invoke()
            verify(arrangement.recoverMLSConversationsUseCase)
                .suspendFunction(arrangement.recoverMLSConversationsUseCase::invoke)
                .wasInvoked(once)
            verify(arrangement.slowSyncRepository)
                .suspendFunction(arrangement.slowSyncRepository::setNeedsToRecoverMLSGroups)
                .with(eq(false))
                .wasInvoked(once)
        }

    @Test
    fun givenMLSNeedsRecoveryFalse_whenObservingAndSyncFinishes_recoverMLSConversationNotPerformed() =
        runTest {
            val (arrangement, mlsConversationsRecoveryManager) = Arrangement()
                .withIsMLSSupported(true)
                .withRecoverMLSConversationsResult(RecoverMLSConversationsResult.Success)
                .withMLSNeedsRecoveryReturn(false)
                .withHasRegisteredMLSClient(true)
                .withIncrementalSyncState(IncrementalSyncStatus.Live)
                .arrange()

            mlsConversationsRecoveryManager.invoke()

            verify(arrangement.recoverMLSConversationsUseCase)
                .suspendFunction(arrangement.recoverMLSConversationsUseCase::invoke)
                .wasNotInvoked()
            verify(arrangement.slowSyncRepository)
                .suspendFunction(arrangement.slowSyncRepository::setNeedsToRecoverMLSGroups)
                .with(anything())
                .wasNotInvoked()
        }

    @Test
    fun givenMLSSupportIsDisabled_whenObservingAndSyncFinishes_recoverMLSConversationsUseCaseNotPerformed() =
        runTest {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(false)
                .withIncrementalSyncState(IncrementalSyncStatus.Live)
                .arrange()

            verify(arrangement.recoverMLSConversationsUseCase)
                .suspendFunction(arrangement.recoverMLSConversationsUseCase::invoke)
                .wasNotInvoked()
            verify(arrangement.slowSyncRepository)
                .suspendFunction(arrangement.slowSyncRepository::setNeedsToRecoverMLSGroups)
                .with(anything())
                .wasNotInvoked()
        }

    @Test
    fun givenMLSClientHasNotBeenRegistered_whenObservingAndSyncFinishes_recoverMLSConversationsUseCaseNotPerformed() =
        runTest {

            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(false)
                .withIsMLSSupported(false)
                .withIncrementalSyncState(IncrementalSyncStatus.Live)
                .arrange()

            verify(arrangement.recoverMLSConversationsUseCase)
                .suspendFunction(arrangement.recoverMLSConversationsUseCase::invoke)
                .wasNotInvoked()
        }

    @Test
    fun givenRecoverMLSConversationFails_whenObservingAndSyncFinishes_updateMLSNeedsRecoveryNotCalled() =
        runTest {
            val (arrangement, mlsConversationsRecoveryManager) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withRecoverMLSConversationsResult(RecoverMLSConversationsResult.Failure(StorageFailure.DataNotFound))
                .withMLSNeedsRecoveryReturn(true)
                .withIncrementalSyncState(IncrementalSyncStatus.Live)
                .arrange()

            mlsConversationsRecoveryManager.invoke()

            verify(arrangement.recoverMLSConversationsUseCase)
                .suspendFunction(arrangement.recoverMLSConversationsUseCase::invoke)
                .wasInvoked(once)
            verify(arrangement.slowSyncRepository)
                .suspendFunction(arrangement.slowSyncRepository::setNeedsToRecoverMLSGroups)
                .with(anything())
                .wasNotInvoked()
        }

    private class Arrangement {
        @Mock
        val incrementalSyncRepository: IncrementalSyncRepository = mock(classOf<IncrementalSyncRepository>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val featureSupport = mock(classOf<FeatureSupport>())

        @Mock
        val recoverMLSConversationsUseCase = mock(classOf<RecoverMLSConversationsUseCase>())

        @Mock
        val slowSyncRepository = mock(classOf<SlowSyncRepository>())

        fun withMLSNeedsRecoveryReturn(state: Boolean) = apply {
            given(slowSyncRepository)
                .suspendFunction(slowSyncRepository::needsToRecoverMLSGroups)
                .whenInvoked()
                .thenReturn(state)
        }

        fun withRecoverMLSConversationsResult(result: RecoverMLSConversationsResult) = apply {
            given(recoverMLSConversationsUseCase)
                .suspendFunction(recoverMLSConversationsUseCase::invoke)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(supported)
        }

        fun withHasRegisteredMLSClient(result: Boolean) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::hasRegisteredMLSClient)
                .whenInvoked()
                .thenReturn(Either.Right(result))
        }

        fun withIncrementalSyncState(state: IncrementalSyncStatus) = apply {
            given(incrementalSyncRepository)
                .getter(incrementalSyncRepository::incrementalSyncState)
                .whenInvoked()
                .thenReturn(flowOf(state))
        }

        fun arrange() = this to MLSConversationsRecoveryManagerImpl(
            featureSupport,
            incrementalSyncRepository,
            clientRepository,
            recoverMLSConversationsUseCase,
            slowSyncRepository,
            kaliumLogger
        )
    }
}
