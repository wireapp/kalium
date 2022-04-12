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

data class SSORedirects(val success: String, val error: String)

interface SSOInitiateLoginUseCase {
    sealed class Param {
        abstract val ssoCode: String
        abstract val serverConfig: ServerConfig
        data class NoRedirect(override val ssoCode: String, override val serverConfig: ServerConfig): Param()
        data class Redirect(override val ssoCode: String, val redirects: SSORedirects, override val serverConfig: ServerConfig): Param()
    }
    suspend operator fun invoke(param: Param): SSOInitiateLoginResult
}

internal class SSOInitiateLoginUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository,
    private val validateUUIDUseCase: ValidateUUIDUseCase
) : SSOInitiateLoginUseCase {

    override suspend fun invoke(param: SSOInitiateLoginUseCase.Param): SSOInitiateLoginResult {
        return when {
            !validateUUIDUseCase.invoke(param.ssoCode) -> return SSOInitiateLoginResult.Failure.InvalidCode
            param is SSOInitiateLoginUseCase.Param.Redirect ->
                ssoLoginRepository.initiate(param.ssoCode, param.redirects.success, param.redirects.error, param.serverConfig)
            else -> ssoLoginRepository.initiate(param.ssoCode, param.serverConfig)
        }.fold({
            if(it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                if(it.kaliumException.errorResponse.code == HttpStatusCode.BadRequest.value)
                    return@fold SSOInitiateLoginResult.Failure.InvalidRedirect
                if(it.kaliumException.errorResponse.code == HttpStatusCode.NotFound.value)
                    return@fold SSOInitiateLoginResult.Failure.InvalidCode
            }
            SSOInitiateLoginResult.Failure.Generic(it)
        }, {
            SSOInitiateLoginResult.Success(it)
        })
    }
}
