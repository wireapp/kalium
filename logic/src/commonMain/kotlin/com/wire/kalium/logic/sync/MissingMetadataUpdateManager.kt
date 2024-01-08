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
package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys.LAST_MISSING_METADATA_SYNC_CHECK
import com.wire.kalium.logic.feature.conversation.RefreshConversationsWithoutMetadataUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
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
import kotlin.time.Duration.Companion.hours

/**
 * This class is responsible for checking if there are any users or conversations without metadata
 * and if so, it will refresh them.
 *
 * The criteria for this, is in a window of 3 hours since the last time this was performed.
 */
internal interface MissingMetadataUpdateManager

internal class MissingMetadataUpdateManagerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val refreshUsersWithoutMetadata: Lazy<RefreshUsersWithoutMetadataUseCase>,
    private val refreshConversationsWithoutMetadata: Lazy<RefreshConversationsWithoutMetadataUseCase>,
    private val timestampKeyRepository: Lazy<TimestampKeyRepository>,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : MissingMetadataUpdateManager {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)
    private val missingMetadataUpdateScope = CoroutineScope(dispatcher)
    private var missingMetadataUpdateJob: Job? = null

    init {
        missingMetadataUpdateJob = missingMetadataUpdateScope.launch {
            incrementalSyncRepository.incrementalSyncState.collect { syncState ->
                ensureActive()
                if (syncState is IncrementalSyncStatus.Live) {
                    updateMissingMetadataIfNeeded()
                }
            }
        }
    }

    private suspend fun updateMissingMetadataIfNeeded() {
        timestampKeyRepository.value.hasPassed(LAST_MISSING_METADATA_SYNC_CHECK, MIN_TIME_BETWEEN_METADATA_SYNCS)
            .flatMap { needsSync ->
                if (needsSync) {
                    kaliumLogger.d("Syncing users and conversations without metadata")
                    refreshConversationsWithoutMetadata.value()
                    refreshUsersWithoutMetadata.value()
                    timestampKeyRepository.value.reset(LAST_MISSING_METADATA_SYNC_CHECK)
                } else {
                    kaliumLogger.d("Skipping syncing users and conversations without metadata")
                    Either.Right(Unit)
                }
            }.onFailure {
                kaliumLogger.w("Error while syncing users and conversations without metadata $it")
            }
    }

    internal companion object {
        val MIN_TIME_BETWEEN_METADATA_SYNCS = 3.hours
    }
}
