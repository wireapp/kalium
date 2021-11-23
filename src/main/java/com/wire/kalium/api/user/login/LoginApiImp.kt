package com.wire.kalium.api.user.login

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.user.login.LoginApi.Companion.PATH_LOGIN
import com.wire.kalium.api.user.login.LoginApi.Companion.QUERY_PERSIST
import com.wire.kalium.api.wrapKaliumResponse
import com.wire.kalium.exceptions.AuthException
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse


class LoginApiImp(private val httpClient: HttpClient) : LoginApi {

    override suspend fun emailLogin(
            loginWithEmailRequest: LoginWithEmailRequest,
            persist: Boolean
    ): KaliumHttpResult<LoginWithEmailResponse> = wrapKaliumResponse<LoginWithEmailResponse> {
            httpClient.post<HttpResponse>(path = PATH_LOGIN) {
                parameter(QUERY_PERSIST, persist)
                body = loginWithEmailRequest
            }.receive()
    }
}
