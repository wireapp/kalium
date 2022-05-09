package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode

sealed class SSOFinalizeLoginResult {
    data class Success(val requestUrl: String) : SSOFinalizeLoginResult()

    sealed class Failure : SSOFinalizeLoginResult() {
        object InvalidCookie : SSOFinalizeLoginResult.Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

interface SSOFinalizeLoginUseCase {
    suspend operator fun invoke(cookie: String, serverConfig: ServerConfig): SSOFinalizeLoginResult
}

internal class SSOFinalizeLoginUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository
) : SSOFinalizeLoginUseCase {

    override suspend fun invoke(cookie: String, serverConfig: ServerConfig): SSOFinalizeLoginResult = suspending {
        ssoLoginRepository.finalize(cookie, serverConfig).coFold({
            if(it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                if(it.kaliumException.errorResponse.code == HttpStatusCode.BadRequest.value)
                    return@coFold SSOFinalizeLoginResult.Failure.InvalidCookie
            }
            SSOFinalizeLoginResult.Failure.Generic(it)
        }, {
            SSOFinalizeLoginResult.Success(it)
        })
    }
}
