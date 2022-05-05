package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.api.model.toSessionDto
import com.wire.kalium.network.utils.CustomErrors
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Url
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
    ): NetworkResponse<SessionDTO> =
        wrapKaliumResponse<AccessTokenDTO> {
            httpClient.post {
                setUrl(Url(apiBaseUrl), PATH_LOGIN)
                parameter(QUERY_PERSIST, persist)
                setBody(param.toRequestBody())
            }
        }.flatMap { accessTokenDTOResponse ->
            with(accessTokenDTOResponse) {
                cookies[RefreshTokenProperties.COOKIE_NAME]?.let { refreshToken ->
                    NetworkResponse.Success(refreshToken, headers, httpCode)
                } ?: CustomErrors.MISSING_REFRESH_TOKEN
            }.mapSuccess { Pair(accessTokenDTOResponse.value, it) }
        }.flatMap { tokensPairResponse ->
            // this is a hack to get the user QualifiedUserId on login
            // TODO: remove this one when login endpoint return a QualifiedUserId
            wrapKaliumResponse<UserDTO> {
                httpClient.get {
                    setUrl(Url(apiBaseUrl), PATH_SELF)
                    bearerAuth(tokensPairResponse.value.first.value)
                }
            }.mapSuccess {
                with(tokensPairResponse.value) {
                    first.toSessionDto(second, it.id)
                }
            }
        }

    private companion object {
        const val PATH_SELF = "self"
        const val PATH_LOGIN = "login"
        const val QUERY_PERSIST = "persist"
    }
}
