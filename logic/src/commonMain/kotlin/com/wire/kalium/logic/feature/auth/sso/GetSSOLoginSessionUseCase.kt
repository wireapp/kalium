package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.AuthTokens
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.withContext

sealed class SSOLoginSessionResult {
    data class Success(val authTokens: AuthTokens, val ssoId: SsoId?, val proxyCredentials: ProxyCredentials?) : SSOLoginSessionResult()

    sealed class Failure : SSOLoginSessionResult() {
        object InvalidCookie : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

/**
 * Obtains a session from the server using the provided cookie
 */
interface GetSSOLoginSessionUseCase {
    /**
     * @param cookie the cookie to use for the login
     * @return the [SSOLoginSessionResult] with tokens and proxy credentials
     */
    suspend operator fun invoke(cookie: String): SSOLoginSessionResult
}

internal class GetSSOLoginSessionUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository,
    private val proxyCredentials: ProxyCredentials?,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : GetSSOLoginSessionUseCase {

    override suspend fun invoke(cookie: String): SSOLoginSessionResult = withContext(dispatcher.default) {
        ssoLoginRepository.provideLoginSession(cookie).fold({
            if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                if (it.kaliumException.errorResponse.code == HttpStatusCode.BadRequest.value)
                    return@fold SSOLoginSessionResult.Failure.InvalidCookie
            }
            SSOLoginSessionResult.Failure.Generic(it)
        }, {
            SSOLoginSessionResult.Success(
                authTokens = sessionMapper.fromSessionDTO(it.sessionDTO),
                ssoId = idMapper.toSsoId(it.userDTO.ssoID),
                proxyCredentials = proxyCredentials
            )
        })
    }
}
