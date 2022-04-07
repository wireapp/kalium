package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.Url

interface SSOLoginApi {

    sealed class InitiateParam(val code: String) {
        class NoRedirect(code: String): InitiateParam(code)
        class Redirect(val success: String, val error: String, code: String): InitiateParam(code)
    }

    suspend fun initiate(param: InitiateParam, apiBaseUrl: String): NetworkResponse<SSOResponse>

    suspend fun finalize(cookie: String, apiBaseUrl: String): NetworkResponse<String>

    suspend fun metaData(apiBaseUrl: String): NetworkResponse<String> // TODO: ask about the response model since it's xml in swagger with no model

    suspend fun settings(apiBaseUrl: String): NetworkResponse<SSOSettingsResponse>
}

class SSOLoginApiImpl(private val httpClient: HttpClient) : SSOLoginApi {
    override suspend fun initiate(param: SSOLoginApi.InitiateParam, apiBaseUrl: String): NetworkResponse<SSOResponse> =
        wrapKaliumResponse {
            httpClient.head {
                setUrl(Url(apiBaseUrl), listOf(PATH_SSO, PATH_INITIATE, param.code))
                when(param) {
                    is SSOLoginApi.InitiateParam.Redirect -> {
                        parameter(QUERY_SUCCESS_REDIRECT, param.success)
                        parameter(QUERY_ERROR_REDIRECT, param.error)
                    }
                    is SSOLoginApi.InitiateParam.NoRedirect -> Unit // do nothing
                }
            }
        }

    override suspend fun finalize(cookie: String, apiBaseUrl: String): NetworkResponse<String> = wrapKaliumResponse {
        httpClient.post {
            setUrl(apiBaseUrl, listOf(PATH_SSO, PATH_FINALIZE))
            header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=$cookie")
        }
    }

    override suspend fun metaData(apiBaseUrl: String): NetworkResponse<String> = wrapKaliumResponse {
        httpClient.get {
            setUrl(apiBaseUrl, listOf(PATH_SSO, PATH_METADATA))
        }
    }

    override suspend fun settings(apiBaseUrl: String): NetworkResponse<SSOSettingsResponse> = wrapKaliumResponse {
        httpClient.get {
            setUrl(apiBaseUrl, listOf(PATH_SSO, PATH_SETTINGS))
        }
    }


    private companion object {
        const val PATH_SSO = "sso"
        const val PATH_INITIATE = "initiate-login"
        const val PATH_FINALIZE = "finalize-login"
        const val PATH_METADATA = "metadata"
        const val PATH_SETTINGS = "settings"
        const val QUERY_SUCCESS_REDIRECT = "success_redirect"
        const val QUERY_ERROR_REDIRECT = "error_redirect"
    }
}
