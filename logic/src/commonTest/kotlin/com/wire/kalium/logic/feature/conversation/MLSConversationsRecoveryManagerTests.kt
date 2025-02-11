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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.kaliumLogger
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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
            coVerify {
                arrangement.recoverMLSConversationsUseCase.invoke()
            }.wasInvoked(once)
            coVerify {
                arrangement.slowSyncRepository.setNeedsToRecoverMLSGroups(eq(false))
            }.wasInvoked(once)
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

            coVerify {
                arrangement.recoverMLSConversationsUseCase.invoke()
            }.wasNotInvoked()
            coVerify {
                arrangement.slowSyncRepository.setNeedsToRecoverMLSGroups(any())
            }.wasNotInvoked()
        }

    @Test
    fun givenMLSSupportIsDisabled_whenObservingAndSyncFinishes_recoverMLSConversationsUseCaseNotPerformed() =
        runTest {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(false)
                .withIncrementalSyncState(IncrementalSyncStatus.Live)
                .arrange()

            coVerify {
                arrangement.recoverMLSConversationsUseCase.invoke()
            }.wasNotInvoked()
            coVerify {
                arrangement.slowSyncRepository.setNeedsToRecoverMLSGroups(any())
            }.wasNotInvoked()
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

            coVerify {
                arrangement.recoverMLSConversationsUseCase.invoke()
            }.wasNotInvoked()
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

            coVerify {
                arrangement.recoverMLSConversationsUseCase.invoke()
            }.wasInvoked(once)
            coVerify {
                arrangement.slowSyncRepository.setNeedsToRecoverMLSGroups(any())
            }.wasNotInvoked()
        }

    private class Arrangement {
        @Mock
        val incrementalSyncRepository: IncrementalSyncRepository = mock(IncrementalSyncRepository::class)

        @Mock
        val clientRepository = mock(ClientRepository::class)

        @Mock
        val featureSupport = mock(FeatureSupport::class)

        @Mock
        val recoverMLSConversationsUseCase = mock(RecoverMLSConversationsUseCase::class)

        @Mock
        val slowSyncRepository = mock(SlowSyncRepository::class)

        suspend fun withMLSNeedsRecoveryReturn(state: Boolean) = apply {
            coEvery {
                slowSyncRepository.needsToRecoverMLSGroups()
            }.returns(state)
        }

        suspend fun withRecoverMLSConversationsResult(result: RecoverMLSConversationsResult) = apply {
            coEvery {
                recoverMLSConversationsUseCase.invoke()
            }.returns(result)
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            }.returns(supported)
        }

        suspend fun withHasRegisteredMLSClient(result: Boolean) = apply {
            coEvery {
                clientRepository.hasRegisteredMLSClient()
            }.returns(Either.Right(result))
        }

        fun withIncrementalSyncState(state: IncrementalSyncStatus) = apply {
            every { incrementalSyncRepository.incrementalSyncState }.returns(flowOf(state))
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
