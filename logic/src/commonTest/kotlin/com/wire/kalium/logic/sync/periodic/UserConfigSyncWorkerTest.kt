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
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.e2ei.ACMECertificatesSyncUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller
import com.wire.kalium.logic.sync.Result
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
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

        arrangement.verifyActions(actionResults) // sync is already live
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
        verifySuspend(VerifyMode.exactly(times)) {
            syncFeatureConfigsUseCase()
            proteusPreKeyRefiller.refillIfNeeded()
            mlsPublicKeysRepository.fetchKeys()
            acmeCertificatesSyncUseCase()
        }
    }

    private suspend fun Arrangement.verifyActions(actionResults: ActionResults) {
        verifySuspend(VerifyMode.exactly(1)) { syncFeatureConfigsUseCase() }

        if (actionResults.syncFeatureConfigsResult.isLeft()) {
            verifySuspend(VerifyMode.not) { mlsPublicKeysRepository.fetchKeys() }
            verifySuspend(VerifyMode.not) { proteusPreKeyRefiller.refillIfNeeded() }
            verifySuspend(VerifyMode.not) { acmeCertificatesSyncUseCase() }
            return
        }

        verifySuspend(VerifyMode.exactly(1)) { mlsPublicKeysRepository.fetchKeys() }

        if (actionResults.mlsPublicKeysFetchResult.isLeft()) {
            verifySuspend(VerifyMode.not) { proteusPreKeyRefiller.refillIfNeeded() }
            verifySuspend(VerifyMode.not) { acmeCertificatesSyncUseCase() }
            return
        }

        verifySuspend(VerifyMode.exactly(1)) { proteusPreKeyRefiller.refillIfNeeded() }

        if (actionResults.proteusPreKeyRefillResult.isLeft()) {
            verifySuspend(VerifyMode.not) { acmeCertificatesSyncUseCase() }
            return
        }

        verifySuspend(VerifyMode.exactly(1)) { acmeCertificatesSyncUseCase() }
    }

    private class Arrangement(private val configure: suspend Arrangement.() -> Unit) {
        val incrementalSyncRepository = mock<com.wire.kalium.logic.data.sync.IncrementalSyncRepository>()
        val syncFeatureConfigsUseCase = mock<SyncFeatureConfigsUseCase>()
        val proteusPreKeyRefiller = mock<ProteusPreKeyRefiller>()
        val mlsPublicKeysRepository = mock<MLSPublicKeysRepository>()
        val acmeCertificatesSyncUseCase = mock<ACMECertificatesSyncUseCase>()
        val timeout = 10.seconds

        suspend fun arrange(): Pair<Arrangement, UserConfigSyncWorker> = run {
            withActionResults(ActionResults())

            configure()
            this@Arrangement to UserConfigSyncWorkerImpl(
                incrementalSyncRepository = incrementalSyncRepository,
                syncFeatureConfigsUseCase = syncFeatureConfigsUseCase,
                proteusPreKeyRefiller = proteusPreKeyRefiller,
                mlsPublicKeysRepository = mlsPublicKeysRepository,
                acmeCertificatesSyncUseCase = acmeCertificatesSyncUseCase,
                kaliumLogger = kaliumLogger,
                dispatchers = dispatchers,
                timeout = timeout,
            )
        }

        fun withIncrementalSyncState(statusFlow: Flow<IncrementalSyncStatus>) {
            every { incrementalSyncRepository.incrementalSyncState } returns statusFlow
        }

        suspend fun withActionResults(actionResults: ActionResults) = apply {
            everySuspend { syncFeatureConfigsUseCase() } returns actionResults.syncFeatureConfigsResult
            everySuspend { proteusPreKeyRefiller.refillIfNeeded() } returns actionResults.proteusPreKeyRefillResult
            everySuspend { mlsPublicKeysRepository.fetchKeys() } returns actionResults.mlsPublicKeysFetchResult
            everySuspend { acmeCertificatesSyncUseCase() } returns Unit
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
