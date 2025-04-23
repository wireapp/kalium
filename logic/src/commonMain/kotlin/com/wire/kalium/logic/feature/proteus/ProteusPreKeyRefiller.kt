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
package com.wire.kalium.logic.feature.proteus

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller.Companion.MAX_PREKEY_ID
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller.Companion.MINIMUM_PREKEYS_COUNT
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller.Companion.REMOTE_PREKEYS_TARGET_COUNT
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import io.mockative.Mockable

@Mockable
internal interface ProteusPreKeyRefiller {
    /**
     * Generates more prekeys and upload them to the backend if needed.
     */
    suspend fun refillIfNeeded(): Either<CoreFailure, Unit>
    companion object {
        const val MINIMUM_PREKEYS_COUNT = 40
        const val REMOTE_PREKEYS_TARGET_COUNT = 100

        /**
         * The number of prekeys that can be generated before
         * resetting back to ID 0.
         * This number is dictated by Cryptobox.
         */
        const val MAX_PREKEY_ID = 65_534
    }
}

/**
 * @param preKeyRepository The repository that is used to operate on prekeys.
 * @param lowOnPrekeysTreshold The minimum of prekeys that will trigger the refill.
 * If the number of available prekeys is lower than this value,
 * the refill will be triggered.
 * @param remotePreKeyTargetCount Number of remaining prekeys in the backend to aim for
 * when generating a new batch if a refill is triggered.
 * @param maxPreKeyId The highest possible prekey ID, before it needs to be reset down to zero.
 * This number is dictated by Cryptobox.
 */
internal class ProteusPreKeyRefillerImpl(
    private val preKeyRepository: PreKeyRepository,
    private val lowOnPrekeysTreshold: Int = MINIMUM_PREKEYS_COUNT,
    private val remotePreKeyTargetCount: Int = REMOTE_PREKEYS_TARGET_COUNT,
    private val maxPreKeyId: Int = MAX_PREKEY_ID,
) : ProteusPreKeyRefiller {
    override suspend fun refillIfNeeded(): Either<CoreFailure, Unit> =
        preKeyRepository.fetchRemotelyAvailablePrekeys().flatMap { remotePreKeys ->
            val isLowOnPrekeys = remotePreKeys.size < lowOnPrekeysTreshold
            if (!isLowOnPrekeys) {
                return Either.Right(Unit)
            }
            // Exclude the last resort prekey from the result
            val rollingPreKeys = remotePreKeys.filter { it <= maxPreKeyId }
            val nextPreKeyBatchSize = remotePreKeyTargetCount - rollingPreKeys.size

            preKeyRepository.mostRecentPreKeyId().flatMap { mostRecentPreKeyId ->
                val wouldNewBatchGoOverTheLimit = mostRecentPreKeyId + nextPreKeyBatchSize >= maxPreKeyId
                val nextBatchStartId = if (wouldNewBatchGoOverTheLimit) {
                    0
                } else {
                    mostRecentPreKeyId + 1
                }
                preKeyRepository.generateNewPreKeys(
                    nextBatchStartId,
                    remotePreKeyTargetCount
                )
            }.flatMap { generatedPreKeys ->
                preKeyRepository.updateMostRecentPreKeyId(generatedPreKeys.last().id).flatMap {
                    preKeyRepository.uploadNewPrekeyBatch(generatedPreKeys)
                }
            }
        }
}
