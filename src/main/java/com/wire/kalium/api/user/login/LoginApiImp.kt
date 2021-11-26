package com.wire.kalium.api.user.login

import com.wire.kalium.api.NetworkResponse
import com.wire.kalium.api.responseHeaders
import com.wire.kalium.api.successValue
import com.wire.kalium.api.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.request.*

class LoginApiImp(private val httpClient: HttpClient) : LoginApi {

    companion object {
        const val PATH_LOGIN = "login"
        const val QUERY_PERSIST = "persist"
    }

    override suspend fun emailLogin(
        loginWithEmailRequest: LoginWithEmailRequest,
        persist: Boolean
    ): NetworkResponse<LoginWithEmailResponse> = wrapKaliumResponse {
        httpClient.post(path = PATH_LOGIN) {
            parameter(QUERY_PERSIST, persist)
            body = loginWithEmailRequest
        }
    }
}
