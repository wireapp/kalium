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

package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys.LAST_KEYING_MATERIAL_UPDATE_CHECK
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

// The duration in hours after which we should re-check keying materials.
internal val KEYING_MATERIAL_CHECK_DURATION = 24.hours

/**
 * Observes MLS conversations last keying material update
 * if a conversation's LastKeyingMaterialUpdate surpassed the threshold then
 * it'll send a new UpdateCommit for that conversation.
 */
internal interface KeyingMaterialsManager

internal class KeyingMaterialsManagerImpl(
    private val featureSupport: FeatureSupport,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val clientRepository: Lazy<ClientRepository>,
    private val updateKeyingMaterialsUseCase: Lazy<UpdateKeyingMaterialsUseCase>,
    private val timestampKeyRepository: Lazy<TimestampKeyRepository>,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : KeyingMaterialsManager {
    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)

    private val updateKeyingMaterialsScope = CoroutineScope(dispatcher)

    private var updateKeyingMaterialsJob: Job? = null

    init {
        updateKeyingMaterialsJob = updateKeyingMaterialsScope.launch {
            incrementalSyncRepository.incrementalSyncState.collect { syncState ->
                ensureActive()
                if (syncState is IncrementalSyncStatus.Live &&
                    featureSupport.isMLSSupported &&
                    clientRepository.value.hasRegisteredMLSClient().getOrElse(false)) {
                    updateKeyingMaterialIfNeeded()
                }
            }
        }
    }

    private suspend fun updateKeyingMaterialIfNeeded() =
        timestampKeyRepository.value.hasPassed(LAST_KEYING_MATERIAL_UPDATE_CHECK, KEYING_MATERIAL_CHECK_DURATION)
            .flatMap { exceeded ->
                if (exceeded) {
                    updateKeyingMaterialsUseCase.value().let { result ->
                        when (result) {
                            is UpdateKeyingMaterialsResult.Failure ->
                                kaliumLogger.w("Error while updating keying materials: ${result.failure}")

                            is UpdateKeyingMaterialsResult.Success ->
                                timestampKeyRepository.value.reset(LAST_KEYING_MATERIAL_UPDATE_CHECK)
                        }
                    }
                }
                Either.Right(Unit)
            }.onFailure { kaliumLogger.w("Error while updating keying materials:: $it") }

}
