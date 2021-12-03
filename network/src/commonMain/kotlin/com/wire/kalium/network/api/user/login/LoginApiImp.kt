package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.api.wrapKaliumResponse
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
    private companion object {
        const val PATH_LOGIN = "login"
        const val QUERY_PERSIST = "persist"
    }
}
