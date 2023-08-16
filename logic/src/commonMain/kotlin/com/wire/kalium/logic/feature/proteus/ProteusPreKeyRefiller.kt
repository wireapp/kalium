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
     * Number of remaining prekeys in the backend to aim for
     * when generating a new batch if a refill is triggered.
     */
    private val remotePreKeyTargetCount: Int = REMOTE_PREKEYS_TARGET_COUNT,
) : ProteusPreKeyRefiller {
    override suspend fun refillIfNeeded(): Either<CoreFailure, Unit> =
        preKeyRepository.fetchRemotelyAvailablePrekeys().flatMap { remotePreKeys ->
            val isLowOnPrekeys = remotePreKeys.size < lowOnPrekeysTreshold
            if (!isLowOnPrekeys) {
                return Either.Right(Unit)
            }
            // Exclude the last resort prekey from the result
            val rollingPreKeys = remotePreKeys.filter { it <= MAX_PREKEY_ID }
            val nextPreKeyBatchSize = remotePreKeyTargetCount - rollingPreKeys.size

            preKeyRepository.mostRecentPreKeyId().flatMap { mostRecentPreKeyId ->
                val wouldNewBatchGoOverTheLimit = mostRecentPreKeyId + nextPreKeyBatchSize >= MAX_PREKEY_ID
                val nextBatchStartId = if(wouldNewBatchGoOverTheLimit) {
                    0
                } else {
                    mostRecentPreKeyId + 1
                }
                preKeyRepository.generateNewPreKeys(
                    nextBatchStartId,
                    remotePreKeyTargetCount
                )
            }
            .flatMap { generatedPreKeys ->
                preKeyRepository.uploadNewPrekeyBatch(generatedPreKeys)
            }
        }

    private companion object {
        private const val MINIMUM_PREKEYS_COUNT = 40
        private const val REMOTE_PREKEYS_TARGET_COUNT = 100

        /**
         * The number of prekeys that can be generated before
         * resetting back to ID 0.
         * This number is dictated by Cryptobox.
         */
        private const val MAX_PREKEY_ID = 65_534
    }
}
