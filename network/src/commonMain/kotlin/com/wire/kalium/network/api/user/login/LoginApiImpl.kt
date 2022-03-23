package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.AuthenticationResult
import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.api.model.toSessionDto
import com.wire.kalium.network.utils.CustomErrors
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.URLProtocol
import io.ktor.http.set

class LoginApiImpl(private val httpClient: HttpClient) : LoginApi {

    override suspend fun login(
        param: LoginApi.LoginParam, persist: Boolean, apiBaseUrl: String
    ): NetworkResponse<AuthenticationResult> = wrapKaliumResponse<AccessTokenDTO> {
        httpClient.post {
            url.set(host = apiBaseUrl, path = PATH_LOGIN)
            url.protocol = URLProtocol.HTTPS

            parameter(QUERY_PERSIST, persist)
            setBody(param.toBody())
        }
    }.flatMap { accessTokenDTOResponse ->
        with(accessTokenDTOResponse) {
            cookies[RefreshTokenProperties.COOKIE_NAME]?.let { refreshToken ->
                NetworkResponse.Success(value.toSessionDto(refreshToken), headers, httpCode)
            } ?: CustomErrors.MISSING_REFRESH_TOKEN
        }
    }.flatMap { sessionDTOResponse ->
        wrapKaliumResponse<UserDTO> {
            httpClient.get {
                url.set(host = apiBaseUrl, path = PATH_SELF)
                url.protocol = URLProtocol.HTTPS
                bearerAuth(sessionDTOResponse.value.accessToken)
            }
        }.mapSuccess { userDTO -> AuthenticationResult(sessionDTOResponse.value, userDTO) }
    }


    private companion object {
        const val PATH_SELF = "self"
        const val PATH_LOGIN = "login"
        const val QUERY_PERSIST = "persist"
    }
}
