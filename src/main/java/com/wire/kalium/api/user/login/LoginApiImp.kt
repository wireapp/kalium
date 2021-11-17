package com.wire.kalium.api.user.login

import com.wire.kalium.api.user.login.LoginApi.Companion.BASE_URL
import com.wire.kalium.api.user.login.LoginApi.Companion.PATH_LOGIN
import com.wire.kalium.api.user.login.LoginApi.Companion.QUERY_PERSIST
import com.wire.kalium.exceptions.AuthException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*


class LoginApiImp(private val httpClient: HttpClient) : LoginApi {

    override suspend fun emailLogin(loginWithEmailRequest: LoginWithEmailRequest, persist: Boolean): LoginWithEmailResponse {
        val response = httpClient.post<HttpResponse>(urlString = "$BASE_URL$PATH_LOGIN") {
            parameter(QUERY_PERSIST, persist)
            body = loginWithEmailRequest
        }
        val status = response.status
        if (status.value == 401 or 400 or 429) {
            throw AuthException(code = status.value, message = "status: ${status.description}\n${response.readText()}")
        }
        return response.receive<LoginWithEmailResponse>()
    }
}
