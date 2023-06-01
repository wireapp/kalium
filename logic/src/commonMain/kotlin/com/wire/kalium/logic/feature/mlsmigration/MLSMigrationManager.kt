/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.mlsmigration

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.mlsmigration.MLSMigrationRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Orchestrates the migration from proteus to MLS.
 */
internal interface MLSMigrationManager

// The duration in hours after which we should re-check the MLS migration progress
internal val MLS_MIGRATION_CHECK_DURATION = 24.hours

internal class MLSMigrationManagerImpl(
    private val featureSupport: FeatureSupport,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val clientRepository: Lazy<ClientRepository>,
    private val timestampKeyRepository: Lazy<TimestampKeyRepository>,
    private val mlsMigrationRepository: Lazy<MLSMigrationRepository>,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : MLSMigrationManager {
    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)

    private val mlsMigrationScope = CoroutineScope(dispatcher)

    private var mlsMigrationJob: Job? = null

    init {
        mlsMigrationJob = mlsMigrationScope.launch {
            incrementalSyncRepository.incrementalSyncState.collect { syncState ->
                ensureActive()
                if (syncState is IncrementalSyncStatus.Live &&
                    featureSupport.isMLSSupported &&
                    clientRepository.value.hasRegisteredMLSClient().getOrElse(false)
                ) {
                    updateMigration()
                }
            }
        }
    }

    private suspend fun updateMigration(): Either<CoreFailure, Unit> =
        timestampKeyRepository.value.hasPassed(TimestampKeys.LAST_MLS_MIGRATION_CHECK, MLS_MIGRATION_CHECK_DURATION)
            .flatMap { lastMlsMigrationCheckHasPassed ->
                if (lastMlsMigrationCheckHasPassed) {
                    mlsMigrationRepository.value.fetchMigrationConfiguration()
                        .onSuccess { timestampKeyRepository.value.reset(TimestampKeys.LAST_MLS_MIGRATION_CHECK) }
                        .flatMap {
                            advanceMigration()
                        }
                }
                Either.Right(Unit)
            }

    private suspend fun advanceMigration(): Either<CoreFailure, Unit> =
        mlsMigrationRepository.value.getMigrationConfiguration().getOrNull()?.let { configuration ->
            if (configuration.hasMigrationStarted()) {
                initialiseMigration()
            } else {
                Either.Right(Unit)
            }
        } ?: Either.Right(Unit)

    private suspend fun initialiseMigration(): Either<CoreFailure, Unit> {
        // TODO initialise migration for all team owned conversations and 1:1 conversations
        return Either.Right(Unit)
    }

    private suspend fun finaliseMigration(): Either<CoreFailure, Unit> {
        // TODO finalise migration for all team owned conversations and 1:1 conversations
        return Either.Right(Unit)
    }
}

fun MLSMigrationModel.hasMigrationStarted(): Boolean {
    return status == Status.ENABLED && Clock.System.now() > startTime
}

fun MLSMigrationModel.hasMigrationEnded(): Boolean {
    return status == Status.ENABLED && Clock.System.now() > endTime
}
