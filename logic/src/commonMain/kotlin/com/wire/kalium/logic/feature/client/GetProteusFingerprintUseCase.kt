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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.common.functional.fold

/**
 * Use case to get fingerprint of current user client (device). Only applies to proteus devices.
 */
interface GetProteusFingerprintUseCase {
    suspend operator fun invoke(): GetProteusFingerprintResult
}

class GetProteusFingerprintUseCaseImpl internal constructor(
    private val preKeyRepository: PreKeyRepository
) : GetProteusFingerprintUseCase {
    override suspend fun invoke(): GetProteusFingerprintResult {
        return preKeyRepository.getLocalFingerprint().fold({
            GetProteusFingerprintResult.Failure(it)
        }, {
            GetProteusFingerprintResult.Success(it.decodeToString())
        })
    }
}

sealed class GetProteusFingerprintResult {
    data class Success(val fingerprint: String) : GetProteusFingerprintResult()

    data class Failure(val genericFailure: CoreFailure) : GetProteusFingerprintResult()
}
