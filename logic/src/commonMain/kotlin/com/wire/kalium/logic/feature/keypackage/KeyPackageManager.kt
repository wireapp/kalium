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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys.LAST_KEY_PACKAGE_COUNT_CHECK
import com.wire.kalium.logic.feature.conversation.MLSConversationMembershipAuditManager
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

// The duration in hours after which we should re-check key package count.
internal val KEY_PACKAGE_COUNT_CHECK_DURATION = 24.hours

/**
 * Observes the MLS key package count and uploads new key packages when necessary.
 */
internal interface KeyPackageManager

/**
 * Keeps the current MLS client's backend key-package pool replenished.
 *
 * The manager starts observing sync state when it is created. Whenever incremental sync is live,
 * it checks whether MLS is available and the client is registered, then performs the periodic
 * key-package check when its interval elapsed, the local MLS count is low, or membership recovery
 * explicitly requests a check. A successful check is forwarded to [MLSConversationMembershipAuditManager]
 * after the MLS transaction closes so conversation recovery can run independently when required.
 */
@Suppress("LongParameterList")
internal class KeyPackageManagerImpl(
    private val featureSupport: FeatureSupport,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val clientRepository: Lazy<ClientRepository>,
    private val refillKeyPackagesUseCase: Lazy<RefillKeyPackagesUseCase>,
    private val keyPackageCountUseCase: Lazy<MLSKeyPackageCountUseCase>,
    private val timestampKeyRepository: Lazy<TimestampKeyRepository>,
    private val membershipAuditManager: Lazy<MLSConversationMembershipAuditManager>,
    private val transactionProvider: CryptoTransactionProvider,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : KeyPackageManager {
    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)

    private val refillKeyPackagesScope = CoroutineScope(dispatcher)

    private var refillKeyPackageJob: Job? = null

    init {
        refillKeyPackageJob = refillKeyPackagesScope.launch {
            combine(
                incrementalSyncRepository.incrementalSyncState,
                membershipAuditManager.value.observeShouldForceKeyPackageCheck()
            ) { syncState, shouldForceKeyPackageCheck ->
                syncState to shouldForceKeyPackageCheck
            }
                .filter { it.first is IncrementalSyncStatus.Live }
                .collect { (_, shouldForceKeyPackageCheck) ->
                    handleSyncState(shouldForceKeyPackageCheck)
                }
        }
    }

    /**
     * Applies the sync, feature-support, and MLS-client-registration gates before checking packages.
     */
    @Suppress("ReturnCount")
    private suspend fun handleSyncState(
        shouldForceKeyPackageCheck: Boolean,
    ) {
        currentCoroutineContext().ensureActive()

        if (!featureSupport.isMLSSupported) return
        if (!clientRepository.value.hasRegisteredMLSClient().getOrElse(false)) return

        refillKeyPackagesIfNeeded(shouldForceKeyPackageCheck)
    }

    /**
     * Evaluates the periodic and recovery triggers and starts a backend package check when needed.
     */
    private suspend fun refillKeyPackagesIfNeeded(shouldForceKeyPackageCheck: Boolean) {
        shouldCheckKeyPackages(shouldForceKeyPackageCheck)
            .flatMap { shouldCheck ->
                if (shouldCheck) refillKeyPackages() else Either.Right(Unit)
            }
            .onFailure { kaliumLogger.w("Error while refilling key packages or auditing MLS membership: $it") }
    }

    /**
     * Combines the elapsed check interval, cached MLS package count, and audit force-check signal.
     */
    private suspend fun shouldCheckKeyPackages(
        shouldForceKeyPackageCheck: Boolean
    ): Either<CoreFailure, Boolean> =
        timestampKeyRepository.value.hasPassed(LAST_KEY_PACKAGE_COUNT_CHECK, KEY_PACKAGE_COUNT_CHECK_DURATION)
            .map { lastKeyPackageCountCheckHasPassed ->
                val cachedCountNeedsRefill = when (val result = keyPackageCountUseCase.value(fromAPI = false)) {
                    is MLSKeyPackageCountResult.Success -> result.needsRefill
                    // If this fails, the count will be checked again after the next sync.
                    else -> false
                }
                shouldForceKeyPackageCheck || lastKeyPackageCountCheckHasPassed || cachedCountNeedsRefill
            }

    /**
     * Checks and refills backend key packages inside one MLS transaction.
     */
    private suspend fun refillKeyPackages(): Either<CoreFailure, Unit> {
        kaliumLogger.i("Checking if we need to refill key packages")
        return transactionProvider.mlsTransaction("KeyPackageManager") { mlsContext ->
            when (val result = refillKeyPackagesUseCase.value(mlsContext)) {
                is RefillKeyPackagesResult.Success -> Either.Right(result)
                is RefillKeyPackagesResult.Failure -> Either.Left(result.failure)
            }
        }.flatMap { refillResult ->
            handleSuccessfulKeyPackageCheck(refillResult)
        }
    }

    /**
     * Resets the periodic-check timestamp and delegates membership recovery after the MLS transaction.
     */
    private suspend fun handleSuccessfulKeyPackageCheck(
        refillResult: RefillKeyPackagesResult.Success
    ): Either<CoreFailure, Unit> {
        val timestampResetResult = timestampKeyRepository.value.reset(LAST_KEY_PACKAGE_COUNT_CHECK)
        // Membership recovery must still be attempted if resetting the periodic-check timestamp fails.
        return membershipAuditManager.value.auditIfNeeded(refillResult)
            .flatMap { timestampResetResult }
    }
}
