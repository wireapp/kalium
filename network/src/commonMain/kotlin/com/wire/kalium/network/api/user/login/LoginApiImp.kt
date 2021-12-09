package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.request.*

class LoginApiImp(private val httpClient: HttpClient) : LoginApi {

    override suspend fun emailLogin(
        loginWithEmailRequest: LoginWithEmailRequest,
        persist: Boolean
    ): NetworkResponse<LoginWithEmailResponse> = wrapKaliumResponse<LoginWithEmailResponse> {
        httpClient.post(path = PATH_LOGIN) {
            parameter(QUERY_PERSIST, persist)
            body = loginWithEmailRequest
        }
    }

    private companion object {
        const val PATH_LOGIN = "login"
        const val QUERY_PERSIST = "persist"
    }
}
