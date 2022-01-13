package com.wire.kalium.logic.data.login

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.failure.InvalidCredentials
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginWithEmailRequest
import com.wire.kalium.network.api.user.login.LoginWithEmailResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.httpResponseCookies
import com.wire.kalium.network.utils.isSuccessful

class LoginRepository(
    private val loginApi: LoginApi,
    private val clientLabel: String
) {

    suspend fun loginWithEmail(email: String, password: String, shouldPersistClient: Boolean): Either<CoreFailure, AuthSession> {
        val response = loginApi.emailLogin(
            LoginWithEmailRequest(
                email, password, clientLabel
            ), shouldPersistClient
        )
        return if (!response.isSuccessful()) {
            handleFailedApiResponse(response)
        } else {
            handleSuccessfulApiResponse(response)
        }
    }

    private fun handleSuccessfulApiResponse(response: NetworkResponse.Success<LoginWithEmailResponse>): Either<CoreFailure, AuthSession> {
        val refreshToken = response.httpResponseCookies()[RefreshTokenProperties.COOKIE_NAME]
        return if (refreshToken == null) {
            Either.Left(CoreFailure.ServerMiscommunication)
        } else {
            Either.Right(AuthSession(response.value.userId, response.value.accessToken, refreshToken, response.value.tokenType))
        }
    }

    private fun handleFailedApiResponse(response: NetworkResponse.Error<*>) =
        when (response.kException) {
            is KaliumException.InvalidRequestError -> Either.Left(InvalidCredentials)
            is KaliumException.NetworkUnavailableError -> Either.Left(CoreFailure.NoNetworkConnection)
            else -> Either.Left(CoreFailure.Unknown(response.kException))
        }
}
