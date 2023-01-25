package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.functional.fold

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
