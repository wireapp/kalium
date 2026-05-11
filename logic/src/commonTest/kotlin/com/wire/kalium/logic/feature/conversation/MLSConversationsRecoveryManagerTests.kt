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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.recoverMLSConversationsUseCase.invoke(any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.slowSyncRepository.setNeedsToRecoverMLSGroups(eq(false))
            }
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

            verifySuspend(VerifyMode.not) {
                arrangement.recoverMLSConversationsUseCase.invoke(any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.slowSyncRepository.setNeedsToRecoverMLSGroups(any())
            }
        }

    @Test
    fun givenMLSSupportIsDisabled_whenObservingAndSyncFinishes_recoverMLSConversationsUseCaseNotPerformed() =
        runTest {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(false)
                .withIncrementalSyncState(IncrementalSyncStatus.Live)
                .arrange()

            verifySuspend(VerifyMode.not) {
                arrangement.recoverMLSConversationsUseCase.invoke(arrangement.transactionContext)
            }
            verifySuspend(VerifyMode.not) {
                arrangement.slowSyncRepository.setNeedsToRecoverMLSGroups(any())
            }
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

            verifySuspend(VerifyMode.not) {
                arrangement.recoverMLSConversationsUseCase.invoke(arrangement.transactionContext)
            }
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.recoverMLSConversationsUseCase.invoke(any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.slowSyncRepository.setNeedsToRecoverMLSGroups(any())
            }
        }

    private class Arrangement: CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val incrementalSyncRepository: IncrementalSyncRepository = mock()
        val clientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)
        val featureSupport = mock<FeatureSupport>(mode = MockMode.autoUnit)
        val recoverMLSConversationsUseCase = mock<RecoverMLSConversationsUseCase>(mode = MockMode.autoUnit)
        val slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)

        suspend fun withMLSNeedsRecoveryReturn(state: Boolean) = apply {
            everySuspend {
                slowSyncRepository.needsToRecoverMLSGroups()
            } returns state
        }

        suspend fun withRecoverMLSConversationsResult(result: RecoverMLSConversationsResult) = apply {
            everySuspend {
                recoverMLSConversationsUseCase.invoke(any())
            } returns result
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            } returns supported
        }

        suspend fun withHasRegisteredMLSClient(result: Boolean) = apply {
            everySuspend {
                clientRepository.hasRegisteredMLSClient()
            } returns Either.Right(result)
        }

        fun withIncrementalSyncState(state: IncrementalSyncStatus) = apply {
            every { incrementalSyncRepository.incrementalSyncState } returns flowOf(state)
        }

        suspend fun arrange() = this to MLSConversationsRecoveryManagerImpl(
            featureSupport,
            incrementalSyncRepository,
            clientRepository,
            recoverMLSConversationsUseCase,
            slowSyncRepository,
            cryptoTransactionProvider,
            kaliumLogger
        ).also {
            withTransactionReturning(Either.Right(Unit))
        }
    }
}
