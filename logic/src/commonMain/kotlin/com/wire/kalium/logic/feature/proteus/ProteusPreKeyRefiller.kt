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
package com.wire.kalium.logic.feature.proteus

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap

internal interface ProteusPreKeyRefiller {
    /**
     * Generates more prekeys and upload them to the backend if needed.
     */
    suspend fun refillIfNeeded(): Either<CoreFailure, Unit>
}

internal class ProteusPreKeyRefillerImpl(
    private val preKeyRepository: PreKeyRepository,
    /**
     * The minimum of prekeys that will trigger the refill.
     * If the number of available prekeys is lower than this value,
     * the refill will be triggered.
     */
    private val lowOnPrekeysTreshold: Int = MINIMUM_PREKEYS_COUNT,
    /**
     * Number of prekeys to be generated and uploaded to the backend,
     * if a refill is triggered.
     */
    private val newPrekeysCount: Int = NEXT_PREKEY_BATCH_SIZE,
) : ProteusPreKeyRefiller {
    override suspend fun refillIfNeeded(): Either<CoreFailure, Unit> =
        preKeyRepository.fetchRemotelyAvailablePrekeys().flatMap { availableKeys ->
            val isLowOnPrekeys = availableKeys.size < lowOnPrekeysTreshold
            if (!isLowOnPrekeys) {
                Either.Right(Unit)
            } else {
                val lastKnownPrekeyId = availableKeys.maxOrNull() ?: 0
                // TODO: Fill logic to generate preKeys and reset after 65k
                preKeyRepository.generateNewPreKeys(
                    lastKnownPrekeyId,
                    newPrekeysCount
                ).flatMap { generatedPreKeys ->
                    preKeyRepository.uploadNewPrekeyBatch(generatedPreKeys)
                }
            }
        }

    private companion object {
        private const val MINIMUM_PREKEYS_COUNT = 20
        private const val NEXT_PREKEY_BATCH_SIZE = 100
    }
}
