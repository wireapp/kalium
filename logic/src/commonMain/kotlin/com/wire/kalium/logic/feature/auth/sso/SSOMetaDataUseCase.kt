package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.functional.fold

sealed class SSOMetaDataResult {
    data class Success(val metaData: String) : SSOMetaDataResult()

    sealed class Failure : SSOMetaDataResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

interface SSOMetaDataUseCase {
    suspend operator fun invoke(serverConfig: ServerConfig): SSOMetaDataResult
}

internal class SSOMetaDataUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository
) : SSOMetaDataUseCase {

    override suspend fun invoke(serverConfig: ServerConfig): SSOMetaDataResult =
        ssoLoginRepository.metaData(serverConfig).fold({
            SSOMetaDataResult.Failure.Generic(it)
        }, {
            SSOMetaDataResult.Success(it)
        })

}
