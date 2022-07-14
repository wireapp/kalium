package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

// The duration in hours after which we should re-check key package count.
internal val KEY_PACKAGE_COUNT_CHECK_DURATION = 24.hours

interface  KeyPackageManager {

    /**
     * Start periodically checking if key packages needs to be refilled.
     */
    fun startObservingKeyPackageCount()

    /**
     * Stops periodically checking if key packages needs to be refilled.
     */
    fun stopObservingKeyPackageCount()
}

class KeyPackageManagerImpl(private val syncRepository: SyncRepository,
                            private val keyPackageRepository: KeyPackageRepository,
                            private val refillKeyPackagesUseCase: RefillKeyPackagesUseCase,
                            kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : KeyPackageManager {
    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)


    private val refillKeyPackagesScope = CoroutineScope(dispatcher)

    private var refillKeyPackageJob: Job? = null

    override fun startObservingKeyPackageCount() {
        refillKeyPackageJob = refillKeyPackagesScope.launch {
            syncRepository.syncState.cancellable().collect { syncState ->
                if (syncState == SyncState.Live) {
                    refillKeyPackagesIfNeeded()
                }
            }
        }
    }

    override fun stopObservingKeyPackageCount() {
        refillKeyPackageJob?.cancel()
        refillKeyPackageJob = null
    }

    private suspend fun refillKeyPackagesIfNeeded() {
        keyPackageRepository.lastKeyPackageCountCheck().flatMap { timestamp ->
            if (Clock.System.now().minus(timestamp) > KEY_PACKAGE_COUNT_CHECK_DURATION) {
                kaliumLogger.i("Checking if we need to refill key packages")
                when (val result = refillKeyPackagesUseCase()) {
                    is RefillKeyPackagesResult.Success -> keyPackageRepository.updateLastKeyPackageCountCheck(Clock.System.now())
                    is RefillKeyPackagesResult.Failure -> Either.Left(result.failure)
                }
            }
            Either.Right(Unit)
        }.onFailure { kaliumLogger.w("Error while refilling key packages: $it") }
    }

}
