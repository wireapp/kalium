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

package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys.LAST_KEY_PACKAGE_COUNT_CHECK
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.hours

// Other clients claim this client's key packages without the local count changing, so check the backend count at the
// first live sync an hour after the last check.
internal val KEY_PACKAGE_COUNT_CHECK_DURATION = 1.hours

/**
 * Observes the MLS key package count and uploads new key packages when necessary.
 */
internal interface KeyPackageManager {
    /**
     * Checks the backend key package count in the background and refills if needed,
     * without waiting for the hourly check.
     */
    fun scheduleRefill()
}

@Suppress("LongParameterList")
internal class KeyPackageManagerImpl(
    private val featureSupport: FeatureSupport,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val clientRepository: Lazy<ClientRepository>,
    private val refillKeyPackagesUseCase: Lazy<RefillKeyPackagesUseCase>,
    private val keyPackageCountUseCase: Lazy<MLSKeyPackageCountUseCase>,
    private val timestampKeyRepository: Lazy<TimestampKeyRepository>,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : KeyPackageManager {
    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)

    private val refillKeyPackagesScope = CoroutineScope(dispatcher)

    private var refillKeyPackageJob: Job? = null

    /**
     * Each uploaded batch has its own transaction, so two refills could otherwise read the same count and
     * both upload the missing key packages.
     */
    private val refillMutex = Mutex()

    init {
        refillKeyPackageJob = refillKeyPackagesScope.launch {
            incrementalSyncRepository.incrementalSyncState.collect { syncState ->
                ensureActive()
                if (syncState is IncrementalSyncStatus.Live &&
                    featureSupport.isMLSSupported &&
                    clientRepository.value.hasRegisteredMLSClient().getOrElse(false)
                ) {
                    refillKeyPackagesIfNeeded(forceCheck = false)
                }
            }
        }
    }

    override fun scheduleRefill() {
        refillKeyPackagesScope.launch {
            refillKeyPackagesIfNeeded(forceCheck = true)
        }
    }

    private suspend fun refillKeyPackagesIfNeeded(forceCheck: Boolean) {
        refillMutex.withLock {
            val hasLastCheckPassed = if (forceCheck) {
                Either.Right(true)
            } else {
                timestampKeyRepository.value.hasPassed(LAST_KEY_PACKAGE_COUNT_CHECK, KEY_PACKAGE_COUNT_CHECK_DURATION)
            }
            hasLastCheckPassed
                .flatMap { lastKeyPackageCountCheckHasPassed ->
                    if (lastKeyPackageCountCheckHasPassed || localCountNeedsRefill()) {
                        kaliumLogger.i("Checking if we need to refill key packages")
                        when (val result = refillKeyPackagesUseCase.value()) {
                            is RefillKeyPackagesResult.Success -> {
                                timestampKeyRepository.value.reset(LAST_KEY_PACKAGE_COUNT_CHECK)
                                Either.Right(Unit)
                            }
                            is RefillKeyPackagesResult.Failure -> {
                                Either.Left(result.failure)
                            }
                        }
                    } else {
                        Either.Right(Unit)
                    }
                }.onFailure { kaliumLogger.w("Error while refilling key packages: $it") }
        }
    }

    private suspend fun localCountNeedsRefill(): Boolean =
        when (val result = keyPackageCountUseCase.value(fromAPI = false)) {
            is MLSKeyPackageCountResult.Success -> result.needsRefill
            // if that fails, during the next sync we will check again! so it doesn't matter to skip one time!
            else -> false
        }
}
