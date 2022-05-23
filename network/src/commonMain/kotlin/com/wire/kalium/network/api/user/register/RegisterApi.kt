package com.wire.kalium.network.api.user.register

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.NewUserDTO
import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.utils.CustomErrors
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders

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
                managedByDTO = null,
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
                managedByDTO = null,
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
        param: RegisterParam
    ): NetworkResponse<Pair<UserDTO, SessionDTO>>

    suspend fun requestActivationCode(
        param: RequestActivationCodeParam
    ): NetworkResponse<Unit>

    suspend fun activate(
        param: ActivationParam
    ): NetworkResponse<Unit>
}


class RegisterApiImpl internal constructor(
    private val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : RegisterApi {

    private val httpClient get() = unauthenticatedNetworkClient.httpClient

    private suspend fun getToken(refreshToken: String): NetworkResponse<AccessTokenDTO> = wrapKaliumResponse {
        httpClient.post(PATH_ACCESS) {
            header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=$refreshToken")
        }
    }

    override suspend fun register(
        param: RegisterApi.RegisterParam
    ): NetworkResponse<Pair<UserDTO, SessionDTO>> = wrapKaliumResponse<UserDTO> {
        httpClient.post(REGISTER_PATH) {
            setBody(param.toBody())
        }
    }.flatMap { registerResponse ->
        registerResponse.cookies[RefreshTokenProperties.COOKIE_NAME]?.let { refreshToken ->
            getToken(refreshToken).mapSuccess { accessTokenDTO ->
                Pair(
                    registerResponse.value,
                    SessionDTO(registerResponse.value.id, accessTokenDTO.tokenType, accessTokenDTO.value, refreshToken)
                )
            }
        } ?: run {
            CustomErrors.MISSING_REFRESH_TOKEN
        }
    }

    override suspend fun requestActivationCode(
        param: RegisterApi.RequestActivationCodeParam
    ): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.post("$ACTIVATE_PATH/$SEND_PATH") {
            setBody(param.toBody())
        }
    }

    override suspend fun activate(param: RegisterApi.ActivationParam): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.post(ACTIVATE_PATH) {
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
