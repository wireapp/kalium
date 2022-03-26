package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.toSessionDto
import com.wire.kalium.network.utils.CustomErrors
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.URLProtocol
import io.ktor.http.set
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class LoginApiImpl(private val httpClient: HttpClient) : LoginApi {

    @Serializable
    internal data class LoginRequest(
        @SerialName("email") val email: String? = null,
        @SerialName("handle") val handle: String? = null,
        @SerialName("password") val password: String,
        @SerialName("label") val label: String
    )

    private fun LoginApi.LoginParam.toRequestBody(): LoginRequest {
        return when (this) {
            is LoginApi.LoginParam.LoginWithEmail -> LoginRequest(email = email, password = password, label = label)
            is LoginApi.LoginParam.LoginWithHandel -> LoginRequest(handle = handle, password = password, label = label)
        }
    }

    override suspend fun login(
        param: LoginApi.LoginParam, persist: Boolean, apiBaseUrl: String
    ): NetworkResponse<SessionDTO> {

        val result = wrapKaliumResponse<AccessTokenDTO> {
            httpClient.post {
                url.set(host = apiBaseUrl, path = PATH_LOGIN)
                url.protocol = URLProtocol.HTTPS

                parameter(QUERY_PERSIST, persist)
                setBody(param.toRequestBody())
            }
        }
        return when (result) {
            is NetworkResponse.Success -> {
                val refreshToken = result.cookies[RefreshTokenProperties.COOKIE_NAME]
                if (refreshToken == null) {
                    CustomErrors.MISSING_REFRESH_TOKEN
                } else {
                    NetworkResponse.Success(result.value.toSessionDto(refreshToken), result.headers, result.httpCode)
                }
            }
            is NetworkResponse.Error -> NetworkResponse.Error(result.kException)
        }
    }


    private companion object {
        const val PATH_LOGIN = "login"
        const val QUERY_PERSIST = "persist"
    }
}
