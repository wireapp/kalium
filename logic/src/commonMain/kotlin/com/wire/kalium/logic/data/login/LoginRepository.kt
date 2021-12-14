package com.wire.kalium.logic.data.login

import com.wire.kalium.logic.GenericFailure
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.LoginUsingEmailUseCase
import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginWithEmailRequest
import com.wire.kalium.network.api.user.login.LoginWithEmailResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.httpResponseCookies
import com.wire.kalium.network.utils.isSuccessful

class LoginRepository(private val loginApi: LoginApi, private val clientLabel: String) {

    //TODO Either<Error, Success> instead of UseCase return types
    suspend fun loginWithEmail(email: String, password: String, shouldPersistClient: Boolean): LoginUsingEmailUseCase.Result {
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

    private fun handleSuccessfulApiResponse(response: NetworkResponse.Success<LoginWithEmailResponse>): LoginUsingEmailUseCase.Result {
        val refreshToken = response.httpResponseCookies()[RefreshTokenProperties.COOKIE_NAME]
        return if (refreshToken == null) {
            LoginUsingEmailUseCase.Result.Failure.Generic(GenericFailure.ServerMiscommunication)
        } else {
            val session = AuthSession(response.value.userId, response.value.accessToken, refreshToken, response.value.tokenType)
            LoginUsingEmailUseCase.Result.Success(session)
        }
    }

    private fun handleFailedApiResponse(response: NetworkResponse.Error<*>) =
        when (response.kException) {
            is KaliumException.InvalidRequestError -> LoginUsingEmailUseCase.Result.Failure.InvalidCredentials
            is KaliumException.NetworkUnavailableError -> LoginUsingEmailUseCase.Result.Failure.Generic(GenericFailure.NoNetworkConnection)
            else -> LoginUsingEmailUseCase.Result.Failure.Generic(GenericFailure.UnknownFailure(response.kException.cause))
        }
}
