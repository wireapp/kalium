package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode

sealed class SSOEstablishSessionResult {
    data class Success(val userSession: AuthSession) : SSOEstablishSessionResult()

    sealed class Failure : SSOEstablishSessionResult() {
        object InvalidCookie : SSOEstablishSessionResult.Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

interface SSOEstablishSessionUseCase {
    suspend operator fun invoke(cookie: String, serverConfig: ServerConfig): SSOEstablishSessionResult
}

internal class SSOEstablishSessionUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository,
    private val sessionMapper: SessionMapper
) : SSOEstablishSessionUseCase {

    override suspend fun invoke(cookie: String, serverConfig: ServerConfig): SSOEstablishSessionResult = suspending {
        ssoLoginRepository.ssoEstablishSession(cookie, serverConfig).coFold({
            if(it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                if(it.kaliumException.errorResponse.code == HttpStatusCode.BadRequest.value)
                    return@coFold SSOEstablishSessionResult.Failure.InvalidCookie
            }
            SSOEstablishSessionResult.Failure.Generic(it)
        }, {
            SSOEstablishSessionResult.Success(sessionMapper.fromSessionDTO(it,serverConfig))
        })
    }
}
