package com.wire.kalium.logic.feature.server.publickeys

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKey
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold

interface FetchMLSPublicKeysUseCase {
    suspend operator fun invoke(): FetchMLSPublicKeysResult
}

internal class FetchMLSPublicKeysUseCaseImpl(
    private val mlsPublicKeysRepository: MLSPublicKeysRepository,
    private val userId: UserId,
    private val serverConfigRepository: ServerConfigRepository
) : FetchMLSPublicKeysUseCase {
    override suspend fun invoke(): FetchMLSPublicKeysResult = serverConfigRepository.configForUser(userId).flatMap {
        mlsPublicKeysRepository.fetchMLSPublicKeysAndStore(it.id)
    }.fold({
        FetchMLSPublicKeysResult.Failure.Generic(it)
    }, {
        FetchMLSPublicKeysResult.Success(it)
    })

}

sealed class FetchMLSPublicKeysResult {
    class Success(publicKeys: List<MLSPublicKey>) : FetchMLSPublicKeysResult()

    sealed class Failure : FetchMLSPublicKeysResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
