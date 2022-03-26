package com.wire.kalium.network.api.user.register

import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.NewUserDTO
import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.api.model.toSessionDto
import com.wire.kalium.network.utils.CustomErrors
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol

interface RegisterApi {

    sealed class RegisterParam(
        open val name: String
    ) {
        internal abstract fun toBody(): NewUserDTO

        data class PersonalAccount(
            val email: String,
            val emailCode: String,
            override val name: String,
            val password: String,
        ) : RegisterParam(name) {
            override fun toBody(): NewUserDTO = NewUserDTO(
                email = email,
                emailCode = emailCode,
                password = password,
                name = name,
                accentId = null,
                assets = null,
                invitationCode = null,
                label = null,
                locale = null,
                phone = null,
                phoneCode = null,
                newBindingTeamDTO = null,
                teamCode = null,
                expiresIn = null,
                managedBy = null,
                ssoID = null,
                teamID = null,
                uuid = null
            )
        }

        data class TeamAccount(
            val email: String,
            val emailCode: String,
            override val name: String,
            val password: String,
            val teamName: String,
            val teamIcon: String
        ) : RegisterParam(name) {
            override fun toBody(): NewUserDTO = NewUserDTO(
                email = email,
                emailCode = emailCode,
                password = password,
                name = name,
                accentId = null,
                assets = null,
                invitationCode = null,
                label = null,
                locale = null,
                phone = null,
                phoneCode = null,
                newBindingTeamDTO = NewBindingTeamDTO(
                    currency = null,
                    iconAssetId = teamIcon,
                    iconKey = null,
                    name = teamName,
                ),
                teamCode = null,
                expiresIn = null,
                managedBy = null,
                ssoID = null,
                teamID = null,
                uuid = null
            )
        }
    }

    sealed class RequestActivationCodeParam {
        internal abstract fun toBody(): RequestActivationRequest
        data class Email(
            val email: String
        ) : RequestActivationCodeParam() {
            override fun toBody(): RequestActivationRequest = RequestActivationRequest(email, null, null, null)
        }
    }

    sealed class ActivationParam(val dryRun: Boolean = true) {
        internal abstract fun toBody(): ActivationRequest
        data class Email(
            val email: String, val code: String
        ) : ActivationParam() {
            override fun toBody(): ActivationRequest = ActivationRequest(code = code, dryRun = dryRun, email = email, null, null, null)
        }
    }

    suspend fun register(
        param: RegisterParam, apiBaseUrl: String
    ): NetworkResponse<Pair<UserDTO, SessionDTO>>

    suspend fun requestActivationCode(
        param: RequestActivationCodeParam, apiBaseUrl: String
    ): NetworkResponse<Unit>

    suspend fun activate(
        param: ActivationParam, apiBaseUrl: String
    ): NetworkResponse<Unit>
}


class RegisterApiImpl(
    private val httpClient: HttpClient
) : RegisterApi {

    private suspend fun getToken(refreshToken: String, apiBaseUrl: String): NetworkResponse<AccessTokenDTO> = wrapKaliumResponse {
        httpClient.post {
            url {
                host = apiBaseUrl
                pathSegments = listOf(PATH_ACCESS)
                protocol = URLProtocol.HTTPS
            }
            header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=$refreshToken")
        }
    }

    override suspend fun register(
        param: RegisterApi.RegisterParam, apiBaseUrl: String
    ): NetworkResponse<Pair<UserDTO, SessionDTO>> = wrapKaliumResponse<UserDTO> {
        httpClient.post {
            url {
                host = apiBaseUrl
                pathSegments = listOf(REGISTER_PATH)
                protocol = URLProtocol.HTTPS
            }
            setBody(param.toBody())
        }
    }.flatMap { registerResponse ->
        registerResponse.cookies[RefreshTokenProperties.COOKIE_NAME]?.let { refreshToken ->
            getToken(refreshToken, apiBaseUrl).mapSuccess { accessTokenDTO ->
                Pair(
                    registerResponse.value, accessTokenDTO.toSessionDto(refreshToken)
                )
            }
        } ?: run {
            CustomErrors.MISSING_REFRESH_TOKEN
        }
    }

    override suspend fun requestActivationCode(
        param: RegisterApi.RequestActivationCodeParam, apiBaseUrl: String
    ): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.post {
            url {
                host = apiBaseUrl
                pathSegments = listOf(ACTIVATE_PATH, SEND_PATH)
                protocol = URLProtocol.HTTPS
            }
            setBody(param.toBody())
        }
    }

    override suspend fun activate(param: RegisterApi.ActivationParam, apiBaseUrl: String): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.post {
            url {
                host = apiBaseUrl
                pathSegments = listOf(ACTIVATE_PATH)
                protocol = URLProtocol.HTTPS
            }
            setBody(param.toBody())
        }
    }


    private companion object {
        const val REGISTER_PATH = "register"
        const val ACTIVATE_PATH = "activate"
        const val SEND_PATH = "send"
        const val PATH_ACCESS = "access"
    }

}
