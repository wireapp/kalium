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
package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import kotlinx.coroutines.flow.first

/**
 * Worker that periodically syncs feature flags.
 */
interface FeatureFlagsSyncWorker {
    suspend fun execute()
}

internal class FeatureFlagSyncWorkerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val syncFeatureConfigs: SyncFeatureConfigsUseCase,
    kaliumLogger: KaliumLogger,
) : FeatureFlagsSyncWorker {

    private val logger = kaliumLogger.withTextTag("FeatureFlagSyncWorker")

    override suspend fun execute() {
        logger.d("Starting to monitor")
        incrementalSyncRepository.incrementalSyncState.first {
            it is IncrementalSyncStatus.Live
        }.let {
            syncFeatureConfigs()
        }
    }
}
