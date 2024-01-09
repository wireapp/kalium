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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case that observes the legal hold request.
 */
interface ObserveLegalHoldRequestUseCase {
    operator fun invoke(): Flow<Result>

    sealed class Result {
        data class LegalHoldRequestAvailable(val fingerprint: ByteArray) : Result() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false

                other as LegalHoldRequestAvailable

                return fingerprint.contentEquals(other.fingerprint)
            }

            override fun hashCode(): Int = fingerprint.contentHashCode()
        }
        data object NoLegalHoldRequest : Result()
        data class Failure(val failure: CoreFailure) : Result()
    }

}

internal class ObserveLegalHoldRequestUseCaseImpl internal constructor(
    val userConfigRepository: UserConfigRepository,
    val preKeyRepository: PreKeyRepository
) : ObserveLegalHoldRequestUseCase {
    override fun invoke(): Flow<ObserveLegalHoldRequestUseCase.Result> =
        userConfigRepository.observeLegalHoldRequest().map {
            it.fold(
                { failure ->
                    if (failure is StorageFailure.DataNotFound) {
                        kaliumLogger.i("No legal hold request found")
                        ObserveLegalHoldRequestUseCase.Result.NoLegalHoldRequest
                    } else {
                        kaliumLogger.i("Legal hold request failure: $failure")
                        ObserveLegalHoldRequestUseCase.Result.Failure(failure)
                    }
                },
                { request ->
                    val preKeyCrypto = PreKeyCrypto(request.lastPreKey.id, request.lastPreKey.key)
                    val result = preKeyRepository.getFingerprintForPreKey(preKeyCrypto)
                    result.fold(
                        { failure ->
                            kaliumLogger.i("Legal hold request fingerprint failure: $failure")
                            ObserveLegalHoldRequestUseCase.Result.Failure(failure)
                        },
                        { fingerprint ->
                            ObserveLegalHoldRequestUseCase.Result.LegalHoldRequestAvailable(
                                fingerprint
                            )
                        }
                    )
                }
            )
        }
}
