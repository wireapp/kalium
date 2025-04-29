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
package com.wire.kalium.logic.feature.mlsmigration

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.sync.SyncStateObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Orchestrates the migration from proteus to MLS.
 */

@Suppress("LongParameterList")
class MLSMigrationManager internal constructor(
    private val kaliumConfigs: KaliumConfigs,
    private val isMLSEnabledUseCase: IsMLSEnabledUseCase,
    private val syncStateObserver: SyncStateObserver,
    private val clientRepository: Lazy<ClientRepository>,
    private val timestampKeyRepository: Lazy<TimestampKeyRepository>,
    private val mlsMigrationWorker: Lazy<MLSMigrationWorker>,
    private val userCoroutineScope: CoroutineScope,
) {
    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */

    suspend operator fun invoke() {
        syncStateObserver.waitUntilLiveOrFailure()
            .onSuccess {
                if (
                    isMLSEnabledUseCase() &&
                    clientRepository.value.hasRegisteredMLSClient().getOrElse(false)
                ) {
                    updateMigration()
                }
            }
    }

    private suspend fun updateMigration(): Either<CoreFailure, Unit> =
        timestampKeyRepository.value.hasPassed(
            TimestampKeys.LAST_MLS_MIGRATION_CHECK,
            kaliumConfigs.mlsMigrationInterval
        ).flatMap { lastMlsMigrationCheckHasPassed ->
            kaliumLogger.d("Migration needs to be updated: $lastMlsMigrationCheckHasPassed")
            if (lastMlsMigrationCheckHasPassed) {
                kaliumLogger.d("Running mls migration")
                userCoroutineScope.launch {
                    mlsMigrationWorker.value.runMigration().onSuccess {
                        kaliumLogger.d("Successfully advanced the mls migration")
                        timestampKeyRepository.value.reset(TimestampKeys.LAST_MLS_MIGRATION_CHECK)
                    }
                        .onFailure {
                            kaliumLogger.d("Failure while advancing the mls migration: $it")
                        }
                }.join()
            }
            Either.Right(Unit)
        }
}

fun MLSMigrationModel.hasMigrationStarted(): Boolean {
    return status == Status.ENABLED && startTime?.let { Clock.System.now() > it } ?: false
}

fun MLSMigrationModel.hasMigrationEnded(): Boolean {
    return status == Status.ENABLED && endTime?.let { Clock.System.now() > it } ?: false
}
