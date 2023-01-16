package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

sealed class SSOMetaDataResult {
    data class Success(val metaData: String) : SSOMetaDataResult()

    sealed class Failure : SSOMetaDataResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

/**
 * Gets the SSO metadata
 */
interface SSOMetaDataUseCase {
    /**
     * @return the [SSOMetaDataResult] with the metadata content if successful
     */
    suspend operator fun invoke(): SSOMetaDataResult
}

internal class SSOMetaDataUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository
) : SSOMetaDataUseCase {

    override suspend fun invoke(): SSOMetaDataResult = withContext(KaliumDispatcherImpl.default) {
        ssoLoginRepository.metaData().fold({
            SSOMetaDataResult.Failure.Generic(it)
        }, {
            SSOMetaDataResult.Success(it)
        })
    }
}
