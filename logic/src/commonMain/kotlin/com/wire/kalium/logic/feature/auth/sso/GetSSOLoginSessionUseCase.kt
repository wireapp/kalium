package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode

sealed class SSOLoginSessionResult {
    data class Success(val userSession: AuthSession) : SSOLoginSessionResult()

    sealed class Failure : SSOLoginSessionResult() {
        object InvalidCookie : SSOLoginSessionResult.Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

interface GetSSOLoginSessionUseCase {
    suspend operator fun invoke(cookie: String, serverConfig: ServerConfig): SSOLoginSessionResult
}

internal class GetSSOLoginSessionUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository,
    private val sessionMapper: SessionMapper
) : GetSSOLoginSessionUseCase {

    override suspend fun invoke(cookie: String, serverConfig: ServerConfig): SSOLoginSessionResult =
        ssoLoginRepository.provideLoginSession(cookie, serverConfig).fold({
            if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                if (it.kaliumException.errorResponse.code == HttpStatusCode.BadRequest.value)
                    return@fold SSOLoginSessionResult.Failure.InvalidCookie
            }
            SSOLoginSessionResult.Failure.Generic(it)
        }, {
            SSOLoginSessionResult.Success(sessionMapper.fromSessionDTO(it, serverConfig))
        })

}
