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
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys.LAST_KEYING_MATERIAL_UPDATE_CHECK
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.sync.SyncStateObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

/**
 * Observes MLS conversations last keying material update
 * if a conversation's LastKeyingMaterialUpdate surpassed the threshold then
 * it'll send a new UpdateCommit for that conversation.
 */

class KeyingMaterialsManager internal constructor(
    private val featureSupport: FeatureSupport,
    private val syncStateObserver: SyncStateObserver,
    private val clientRepository: Lazy<ClientRepository>,
    private val updateKeyingMaterialsUseCase: Lazy<UpdateKeyingMaterialsUseCase>,
    private val timestampKeyRepository: Lazy<TimestampKeyRepository>,
    private val userCoroutineScope: CoroutineScope,
) {

    suspend operator fun invoke() {
        syncStateObserver.waitUntilLiveOrFailure().onSuccess {
            if (
                featureSupport.isMLSSupported &&
                clientRepository.value.hasRegisteredMLSClient().getOrElse(false)
            ) {
                userCoroutineScope.launch { updateKeyingMaterialIfNeeded() }.join()
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

    private companion object {
        // The duration in hours after which we should re-check keying materials.
        val KEYING_MATERIAL_CHECK_DURATION = 24.hours
    }
}
