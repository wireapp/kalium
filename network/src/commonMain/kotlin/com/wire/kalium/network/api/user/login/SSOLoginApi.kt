package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.api.model.toSessionDto
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess

interface SSOLoginApi {

    sealed class InitiateParam(open val uuid: String) {
        data class WithoutRedirect(override val uuid: String) : InitiateParam(uuid)
        data class WithRedirect(val success: String, val error: String, override val uuid: String) : InitiateParam(uuid)
    }

    suspend fun initiate(param: InitiateParam, apiBaseUrl: Url): NetworkResponse<String>

    suspend fun finalize(cookie: String, apiBaseUrl: Url): NetworkResponse<String>

    suspend fun ssoEstablishSession(cookie: String, apiBaseUrl: Url): NetworkResponse<SessionDTO>

    suspend fun metaData(apiBaseUrl: Url): NetworkResponse<String> // TODO: ask about the response model since it's xml in swagger with no model

    suspend fun settings(apiBaseUrl: Url): NetworkResponse<SSOSettingsResponse>
}

class SSOLoginApiImpl(private val httpClient: HttpClient) : SSOLoginApi {
    override suspend fun initiate(param: SSOLoginApi.InitiateParam, apiBaseUrl: Url): NetworkResponse<String> {
        val response = httpClient.head {
            setUrl(apiBaseUrl, "$PATH_SSO/$PATH_INITIATE/${param.uuid}")
            if(param is SSOLoginApi.InitiateParam.WithRedirect) {
                parameter(QUERY_SUCCESS_REDIRECT, param.success)
                parameter(QUERY_ERROR_REDIRECT, param.error)
            }
            accept(ContentType.Text.Plain)
        }
        return if (response.status.isSuccess()) {
            NetworkResponse.Success(response.request.url.toString(), response)
        } else {
            wrapKaliumResponse { response }
        }
    }

    override suspend fun finalize(cookie: String, apiBaseUrl: Url): NetworkResponse<String> = wrapKaliumResponse {
        httpClient.post {
            setUrl(apiBaseUrl, PATH_SSO, PATH_FINALIZE)
            header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=$cookie")
        }
    }

    override suspend fun ssoEstablishSession(cookie: String, apiBaseUrl: Url): NetworkResponse<SessionDTO> = wrapKaliumResponse<AccessTokenDTO> {
            httpClient.post {
                setUrl(apiBaseUrl, PATH_ACCESS)
                header(HttpHeaders.Cookie, cookie)
            }
        }
        .flatMap { accessTokenDTOResponse ->
            with(accessTokenDTOResponse) {
                    NetworkResponse.Success(cookie, headers, httpCode)
            }.mapSuccess { Pair(accessTokenDTOResponse.value, it) }
        }.flatMap { tokensPairResponse ->
            // this is a hack to get the user QualifiedUserId on login
            // TODO: remove this one when login endpoint return a QualifiedUserId
            wrapKaliumResponse<UserDTO> {
                httpClient.get {
                    setUrl(apiBaseUrl, PATH_SELF)
                    bearerAuth(tokensPairResponse.value.first.value)
                }
            }.mapSuccess {
                with(tokensPairResponse.value) {
                    first.toSessionDto(second, it.id)
                }
            }
        }

    override suspend fun metaData(apiBaseUrl: Url): NetworkResponse<String> = wrapKaliumResponse {
        httpClient.get {
            setUrl(apiBaseUrl, PATH_SSO, PATH_METADATA)
        }
    }

    override suspend fun settings(apiBaseUrl: Url): NetworkResponse<SSOSettingsResponse> = wrapKaliumResponse {
        httpClient.get {
            setUrl(apiBaseUrl, PATH_SSO, PATH_SETTINGS)
        }
    }


    private companion object {
        const val PATH_SSO = "sso"
        const val PATH_INITIATE = "initiate-login"
        const val PATH_FINALIZE = "finalize-login"
        const val PATH_METADATA = "metadata"
        const val PATH_SETTINGS = "settings"
        const val PATH_ACCESS = "access"
        const val PATH_SELF = "self"
        const val QUERY_SUCCESS_REDIRECT = "success_redirect"
        const val QUERY_ERROR_REDIRECT = "error_redirect"
    }
}
