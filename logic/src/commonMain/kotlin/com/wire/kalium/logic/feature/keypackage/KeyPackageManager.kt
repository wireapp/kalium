package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

// The duration in hours after which we should re-check key package count.
internal val KEY_PACKAGE_COUNT_CHECK_DURATION = 24.hours

/**
 * Observes the MLS key package count and uploads new key packages when necessary.
 */
internal interface KeyPackageManager

internal class KeyPackageManagerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val keyPackageRepository: Lazy<KeyPackageRepository>,
    private val refillKeyPackagesUseCase: Lazy<RefillKeyPackagesUseCase>,
    private val keyPackageCountUseCase: Lazy<MLSKeyPackageCountUseCase>,
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

    init {
        refillKeyPackageJob = refillKeyPackagesScope.launch {
            incrementalSyncRepository.incrementalSyncState.collect { syncState ->
                ensureActive()
                if (syncState is IncrementalSyncStatus.Live) {
                    refillKeyPackagesIfNeeded()
                }
            }
        }
    }

    private suspend fun refillKeyPackagesIfNeeded() {
        keyPackageRepository.value.lastKeyPackageCountCheck().flatMap { timestamp ->
            val forceRefill = keyPackageCountUseCase.value(fromAPI = false).let {
                when (it) {
                    is MLSKeyPackageCountResult.Success -> it.needsRefill
                    // if that fails, during the next sync we will check again! so it doesn't matter to skip one time!
                    else -> false
                }
            }
            if (Clock.System.now().minus(timestamp) > KEY_PACKAGE_COUNT_CHECK_DURATION || forceRefill) {
                kaliumLogger.i("Checking if we need to refill key packages")
                when (val result = refillKeyPackagesUseCase.value()) {
                    is RefillKeyPackagesResult.Success -> keyPackageRepository.value.updateLastKeyPackageCountCheck(Clock.System.now())
                    is RefillKeyPackagesResult.Failure -> Either.Left(result.failure)
                }
            }
            Either.Right(Unit)
        }.onFailure { kaliumLogger.w("Error while refilling key packages: $it") }
    }

}
