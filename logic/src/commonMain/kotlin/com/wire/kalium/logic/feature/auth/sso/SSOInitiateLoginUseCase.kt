package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.feature.auth.ValidateUUIDUseCase
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode

sealed class SSOInitiateLoginResult {
    data class Success(val requestUrl: String) : SSOInitiateLoginResult()

    sealed class Failure : SSOInitiateLoginResult() {
        object InvalidCode : Failure()
        object InvalidRedirect : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

data class Redirects(val success: String, val error: String)

interface SSOInitiateLoginUseCase {
    suspend operator fun invoke(code: String, serverConfig: ServerConfig, redirects: Redirects? = null): SSOInitiateLoginResult
}

internal class SSOInitiateLoginUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository,
    private val validateUUIDUseCase: ValidateUUIDUseCase
) : SSOInitiateLoginUseCase {

    override suspend fun invoke(code: String, serverConfig: ServerConfig, redirects: Redirects?): SSOInitiateLoginResult = suspending {
        when {
            !validateUUIDUseCase.invoke(code) -> return@suspending SSOInitiateLoginResult.Failure.InvalidCode
            redirects != null -> ssoLoginRepository.initiate(code, redirects.success, redirects.error, serverConfig)
            else -> ssoLoginRepository.initiate(code, serverConfig)
        }.coFold({
            if(it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                if(it.kaliumException.errorResponse.code == HttpStatusCode.BadRequest.value)
                    return@coFold SSOInitiateLoginResult.Failure.InvalidRedirect
                if(it.kaliumException.errorResponse.code == HttpStatusCode.NotFound.value)
                    return@coFold SSOInitiateLoginResult.Failure.InvalidCode
            }
            SSOInitiateLoginResult.Failure.Generic(it)
        }, {
            SSOInitiateLoginResult.Success(it)
        })
    }
}
