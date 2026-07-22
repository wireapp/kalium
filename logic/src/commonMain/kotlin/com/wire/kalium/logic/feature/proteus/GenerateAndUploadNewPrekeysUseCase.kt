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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller.Companion.MAX_PREKEY_ID

/**
 * Generates [count] new Proteus prekeys, continuing the client's rolling prekey ID sequence
 * from where it last left off, and uploads them to the backend.
 *
 * Unlike [ProteusPreKeyRefiller], the amount is caller-supplied rather than threshold-driven.
 */
internal interface GenerateAndUploadNewPrekeysUseCase {
    suspend operator fun invoke(count: Int): Either<CoreFailure, Unit>
}

internal class GenerateAndUploadNewPrekeysUseCaseImpl(
    private val preKeyRepository: PreKeyRepository,
    private val maxPreKeyId: Int = MAX_PREKEY_ID,
) : GenerateAndUploadNewPrekeysUseCase {

    override suspend operator fun invoke(count: Int): Either<CoreFailure, Unit> {
        if (count <= 0) return Either.Right(Unit)

        return preKeyRepository.mostRecentPreKeyId().flatMap { mostRecentPreKeyId ->
            val wouldGoOverTheLimit = mostRecentPreKeyId + count >= maxPreKeyId
            val nextBatchStartId = if (wouldGoOverTheLimit) 0 else mostRecentPreKeyId + 1
            preKeyRepository.generateNewPreKeys(nextBatchStartId, count)
        }.flatMap { generatedPreKeys ->
            preKeyRepository.updateMostRecentPreKeyId(generatedPreKeys.last().id).flatMap {
                preKeyRepository.uploadNewPrekeyBatch(generatedPreKeys)
            }
        }
    }
}
