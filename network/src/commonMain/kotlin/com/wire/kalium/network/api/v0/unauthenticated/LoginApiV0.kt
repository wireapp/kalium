package com.wire.kalium.network.api.v0.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.base.model.RefreshTokenProperties
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.UserDTO
import com.wire.kalium.network.api.base.model.toSessionDto
import com.wire.kalium.network.api.base.unAuthenticated.LoginApi
import com.wire.kalium.network.utils.CustomErrors
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class LoginApiV0 internal constructor(
    private val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : LoginApi {

    private val httpClient get() = unauthenticatedNetworkClient.httpClient

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
        param: LoginApi.LoginParam,
        persist: Boolean
    ): NetworkResponse<Pair<SessionDTO, UserDTO>> = wrapKaliumResponse<AccessTokenDTO> {
        httpClient.post(PATH_LOGIN) {
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
        wrapKaliumResponse<UserDTO> {
            httpClient.get(PATH_SELF) {
                bearerAuth(tokensPairResponse.value.first.value)
            }
        }.mapSuccess { userDTO ->
            with(tokensPairResponse.value) {
                Pair(first.toSessionDto(second, userDTO.id), userDTO)
            }
        }
    }

    private companion object {
        const val PATH_SELF = "self"
        const val PATH_LOGIN = "login"
        const val QUERY_PERSIST = "persist"
    }
}
