package com.wire.kalium.network.api.user.register

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.URLProtocol

interface RegisterApi {
    sealed class RegisterParam(
        open val name: String
    ) {
        internal abstract fun toBody(): RegisterRequest
        data class PersonalAccount(
            val email: String,
            val emailCode: String,
            override val name: String,
            val password: String,
        ) : RegisterParam(name) {
            override fun toBody(): RegisterRequest = RegisterRequest(
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
                newBindingTeam = null,
                teamCode = null,
            )
        }
    }

    sealed class SendActivationCodeParam {
        internal abstract fun toBody(): SendActivationRequest
        data class Email(
            val email: String
        ) : SendActivationCodeParam() {
            override fun toBody(): SendActivationRequest = SendActivationRequest(email = email, null, null, null)
        }
    }

    sealed class ActivationParam(val dryRun: Boolean = true) {
        internal abstract fun toBody(): ActivationRequest
        data class Email(
            val email: String,
            val code: String
        ) : ActivationParam() {
            override fun toBody(): ActivationRequest = ActivationRequest(code = code, dryRun = dryRun, email = email, null, null, null)
        }
    }

    suspend fun register(
        param: RegisterParam,
        apiBaseUrl: String
    ): NetworkResponse<RegisterResponse>

    suspend fun sendActivationCode(
        param: SendActivationCodeParam,
        apiBaseUrl: String
    ): NetworkResponse<Unit>

    suspend fun activate(
        param: ActivationParam,
        apiBaseUrl: String
    ): NetworkResponse<Unit>
}


class RegisterApiImpl(private val httpClient: HttpClient) : RegisterApi {
    override suspend fun register(
        param: RegisterApi.RegisterParam, apiBaseUrl: String
    ): NetworkResponse<RegisterResponse> =
        wrapKaliumResponse {
            httpClient.post {
                url {
                    host = apiBaseUrl
                    pathSegments = listOf(REGISTER_PATH)
                    protocol = URLProtocol.HTTPS
                }
                setBody(param.toBody())
            }
        }

    override suspend fun sendActivationCode(
        param: RegisterApi.SendActivationCodeParam,
        apiBaseUrl: String
    ): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post {
                url {
                    host = apiBaseUrl
                    pathSegments = listOf(ACTIVATE_PATH, SEND_PATH)
                    protocol = URLProtocol.HTTPS
                }
                setBody(param.toBody())
            }
        }

    override suspend fun activate(param: RegisterApi.ActivationParam, apiBaseUrl: String): NetworkResponse<Unit> =
        wrapKaliumResponse {
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
    }

}
