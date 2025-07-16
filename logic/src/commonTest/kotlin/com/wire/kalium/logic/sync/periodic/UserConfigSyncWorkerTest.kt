/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.periodic

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.e2ei.SyncCertificateRevocationListUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller
import com.wire.kalium.logic.sync.Result
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangementImpl
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

private val dispatchers = TestKaliumDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class UserConfigSyncWorkerTest {

    @Test
    fun givenSyncIsLive_thenExecuteImmediatelyAndReturnSuccess() = runTest(dispatchers.io) {
        val incrementalSyncStateFlow =
            MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Live)
        val (arrangement, worker) = arrange {
            withIncrementalSyncState(incrementalSyncStateFlow)
        }

        val workerJob = async { worker.doWork() }
        advanceTimeBy(1.seconds)

        arrangement.verifyActions(times = 1) // sync is already live
        assertEquals(Result.Success, workerJob.await())
    }

    @Test
    fun givenSyncIsNotLiveAndBecomesLiveBeforeTimeout_thenExecuteWhenSyncBecomesLiveAndReturnSuccess() =
        runTest(dispatchers.io) {
            val incrementalSyncStateFlow =
                MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Pending)
            val (arrangement, worker) = arrange {
                withIncrementalSyncState(incrementalSyncStateFlow)
            }

            val workerJob = async { worker.doWork() }
            advanceTimeBy(1.seconds)

            arrangement.verifyActions(times = 0) // sync is not live yet

            incrementalSyncStateFlow.value = IncrementalSyncStatus.Live // change sync state to live
            advanceTimeBy(1.seconds)

            arrangement.verifyActions(times = 1) // sync is now live
            assertEquals(Result.Success, workerJob.await())
        }

    @Test
    fun givenSyncIsNotLiveAndDoesNotBecomeLiveBeforeTimeout_thenDoNotExecuteAndReturnRetry() =
        runTest(dispatchers.io) {
            val incrementalSyncStateFlow =
                MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Pending)
            val (arrangement, worker) = arrange {
                withIncrementalSyncState(incrementalSyncStateFlow)
            }

            val workerJob = async { worker.doWork() }
            advanceTimeBy(1.seconds)

            arrangement.verifyActions(times = 0) // sync is not live yet

            advanceTimeBy(UserConfigSyncWorker.TIMEOUT + 1.seconds) // trigger timeout

            arrangement.verifyActions(times = 0) // sync did not become live before timeout
            assertEquals(Result.Retry, workerJob.await())
        }

    private fun testFailedActions(actionResults: ActionResults) = runTest(dispatchers.io) {
        val incrementalSyncStateFlow =
            MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Live)
        val (arrangement, worker) = arrange {
            withIncrementalSyncState(incrementalSyncStateFlow)
            withActionResults(actionResults)
        }

        val workerJob = async { worker.doWork() }
        advanceTimeBy(1.seconds)

        arrangement.verifyActions(times = 1) // sync is already live
        assertEquals(Result.Failure, workerJob.await())
    }

    @Test
    fun givenSyncIsLiveAndFeatureConfigSyncFails_thenReturnFailure() =
        testFailedActions(ActionResults(syncFeatureConfigsResult = NetworkFailure.NoNetworkConnection(null).left()))

    @Test
    fun givenSyncIsLiveAndProteusPreKeyRefillFails_thenReturnFailure() =
        testFailedActions(ActionResults(proteusPreKeyRefillResult = NetworkFailure.NoNetworkConnection(null).left()))

    @Test
    fun givenSyncIsLiveAndMlsPublicKeysFetchFails_thenReturnFailure() =
        testFailedActions(ActionResults(mlsPublicKeysFetchResult = NetworkFailure.NoNetworkConnection(null).left()))

    private suspend fun Arrangement.verifyActions(times: Int = 1) {
        coVerify {
            syncFeatureConfigsUseCase()
            syncCertificateRevocationListUseCase()
            proteusPreKeyRefiller.refillIfNeeded()
            mlsPublicKeysRepository.fetchKeys()
        }.wasInvoked(exactly = times)
    }

    private class Arrangement(private val configure: suspend Arrangement.() -> Unit) :
        IncrementalSyncRepositoryArrangement by IncrementalSyncRepositoryArrangementImpl() {
        val syncCertificateRevocationListUseCase = mock(SyncCertificateRevocationListUseCase::class)
        val syncFeatureConfigsUseCase = mock(SyncFeatureConfigsUseCase::class)
        val proteusPreKeyRefiller = mock(ProteusPreKeyRefiller::class)
        val mlsPublicKeysRepository = mock(MLSPublicKeysRepository::class)
        val timeout = 10.seconds

        suspend fun arrange(): Pair<Arrangement, UserConfigSyncWorker> = run {
            withActionResults(ActionResults())

            configure()
            this@Arrangement to UserConfigSyncWorkerImpl(
                incrementalSyncRepository = incrementalSyncRepository,
                syncCertificateRevocationListUseCase = syncCertificateRevocationListUseCase,
                syncFeatureConfigsUseCase = syncFeatureConfigsUseCase,
                proteusPreKeyRefiller = proteusPreKeyRefiller,
                mlsPublicKeysRepository = mlsPublicKeysRepository,
                kaliumLogger = kaliumLogger,
                dispatchers = dispatchers,
                timeout = timeout,
            )
        }

        suspend fun withActionResults(actionResults: ActionResults) = apply {
            coEvery { syncCertificateRevocationListUseCase() }.returns(Unit)
            coEvery { syncFeatureConfigsUseCase() }.returns(actionResults.syncFeatureConfigsResult)
            coEvery { proteusPreKeyRefiller.refillIfNeeded() }.returns(actionResults.proteusPreKeyRefillResult)
            coEvery { mlsPublicKeysRepository.fetchKeys() }.returns(actionResults.mlsPublicKeysFetchResult)
        }
    }

    data class ActionResults(
        val syncFeatureConfigsResult: Either<CoreFailure, Unit> = Unit.right(),
        val proteusPreKeyRefillResult: Either<CoreFailure, Unit> = Unit.right(),
        val mlsPublicKeysFetchResult: Either<CoreFailure, MLSPublicKeys> = MLSPublicKeys(emptyMap()).right()
    )

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) =
            Arrangement(configure).arrange()
    }
}
