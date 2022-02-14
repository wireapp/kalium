package com.wire.kalium.logic.data.auth.login

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.failure.AuthenticationFailure
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.httpResponseCookies
import com.wire.kalium.network.utils.isSuccessful

interface LoginRepository {
    suspend fun loginWithEmail(email: String, password: String, shouldPersistClient: Boolean, serverConfig: ServerConfig): Either<CoreFailure, AuthSession>
    suspend fun loginWithHandle(handle: String, password: String, shouldPersistClient: Boolean, serverConfig: ServerConfig): Either<CoreFailure, AuthSession>
}

class LoginRepositoryImpl(
    private val loginApi: LoginApi,
    private val clientLabel: String
) : LoginRepository {
    override suspend fun loginWithEmail(email: String, password: String, shouldPersistClient: Boolean, serverConfig: ServerConfig): Either<CoreFailure, AuthSession> =
        login(LoginApi.LoginParam.LoginWithEmail(email, password, clientLabel), shouldPersistClient, serverConfig)

    override suspend fun loginWithHandle(handle: String, password: String, shouldPersistClient: Boolean, serverConfig: ServerConfig): Either<CoreFailure, AuthSession> =
        login(LoginApi.LoginParam.LoginWithHandel(handle, password, clientLabel), shouldPersistClient, serverConfig)


    private suspend fun login(loginParam: LoginApi.LoginParam, persistClient: Boolean, serverConfig: ServerConfig): Either<CoreFailure, AuthSession> {
        val response = loginApi.login(param = loginParam, persist = persistClient, apiBaseUrl = serverConfig.apiBaseUrl)
        return if (!response.isSuccessful()) {
            handleFailedApiResponse(response)
        } else {
            handleSuccessfulApiResponse(response, serverConfig)
        }
    }

    private fun handleSuccessfulApiResponse(response: NetworkResponse.Success<LoginResponse>, serverConfig: ServerConfig): Either<CoreFailure, AuthSession> {
        val refreshToken = response.httpResponseCookies()[RefreshTokenProperties.COOKIE_NAME]
        return if (refreshToken == null) {
            Either.Left(CoreFailure.ServerMiscommunication)
        } else {
            Either.Right(AuthSession(response.value.userId, response.value.accessToken, refreshToken, response.value.tokenType, serverConfig))
        }
    }

    private fun handleFailedApiResponse(response: NetworkResponse.Error<*>) =
        when (response.kException) {
            is KaliumException.InvalidRequestError -> Either.Left(AuthenticationFailure.InvalidCredentials)
            is KaliumException.NetworkUnavailableError -> Either.Left(CoreFailure.NoNetworkConnection)
            else -> Either.Left(CoreFailure.Unknown(response.kException))
        }
}
