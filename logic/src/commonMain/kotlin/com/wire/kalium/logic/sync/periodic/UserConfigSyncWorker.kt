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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.right
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.e2ei.ACMECertificatesSyncUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller
import com.wire.kalium.logic.sync.DefaultWorker
import com.wire.kalium.logic.sync.Result
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker.Companion.NAME
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker.Companion.TIMEOUT
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 *  Worker that is responsible for syncing/refreshing user configurations:
 *  - feature configs
 *  - MLS public keys
 *  - Proteus pre-keys
 *
 *  API version is also part of the user config, but it is updated by [UpdateApiVersionsWorker] and not here.
 */
@Mockable
internal interface UserConfigSyncWorker : DefaultWorker {
    override suspend fun doWork(): Result

    companion object Companion {
        const val NAME: String = "UserConfigSyncWorker"
        val TIMEOUT: Duration = 10.seconds
    }
}

internal class UserConfigSyncWorkerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase,
    private val proteusPreKeyRefiller: ProteusPreKeyRefiller,
    private val mlsPublicKeysRepository: MLSPublicKeysRepository,
    private val acmeCertificatesSyncUseCase: ACMECertificatesSyncUseCase,
    kaliumLogger: KaliumLogger,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val timeout: Duration = TIMEOUT,
) : UserConfigSyncWorker {
    private val logger = kaliumLogger.withTextTag(NAME)

    private val actions: List<suspend () -> Either<CoreFailure, Unit>> = listOf(
        { syncFeatureConfigsUseCase() },
        { mlsPublicKeysRepository.fetchKeys().map { Unit } },
        { proteusPreKeyRefiller.refillIfNeeded() },
        { acmeCertificatesSyncUseCase().let { Unit.right() } },
    )

    override suspend fun doWork(): Result = withContext(dispatchers.io) {
        waitUntilLiveWithTimeout {
            actions
                .foldToEitherWhileRight(Unit) { action, _ -> action() }
                .fold(
                    { failure ->
                        logger.w("Failed to sync user configurations: $failure")
                        Result.Failure
                    },
                    {
                        logger.i("Successfully synced user configurations")
                        Result.Success
                    }
                )
        }
    }

    private suspend fun waitUntilLiveWithTimeout(
        actionWhenLive: suspend () -> Result
    ): Result {
        logger.i("Waiting until live to fetch configurations")
        return withTimeoutOrNull(timeout) {
            incrementalSyncRepository.incrementalSyncState
                .filter { it is IncrementalSyncStatus.Live }
                .first()
        }?.let {
            logger.i("Incremental sync is live, executing")
            actionWhenLive()
        } ?: run {
            logger.w("Incremental sync is not live within the timeout of $timeout ms, scheduling retry later")
            Result.Retry
        }
    }
}
