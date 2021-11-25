package com.wire.kalium.api.user.login

import com.wire.kalium.api.user.login.LoginApi.Companion.PATH_LOGIN
import com.wire.kalium.api.user.login.LoginApi.Companion.QUERY_PERSIST
import io.ktor.client.*
import io.ktor.client.request.*


class LoginApiImp(private val httpClient: HttpClient) : LoginApi {

    override suspend fun emailLogin(
            loginWithEmailRequest: LoginWithEmailRequest,
            persist: Boolean
    ): LoginWithEmailResponse =
            httpClient.post(path = PATH_LOGIN) {
                parameter(QUERY_PERSIST, persist)
                body = loginWithEmailRequest
            }
}
