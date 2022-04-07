package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.host
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url

interface SSOLoginApi {

    sealed class InitiateParam(val code: String) {
        class NoRedirect(code: String) : InitiateParam(code)
        class Redirect(val success: String, val error: String, code: String) : InitiateParam(code)
    }

    suspend fun initiate(param: InitiateParam, apiBaseUrl: Url): NetworkResponse<String>

    suspend fun finalize(cookie: String, apiBaseUrl: Url): NetworkResponse<String>

    suspend fun metaData(apiBaseUrl: Url): NetworkResponse<String> // TODO: ask about the response model since it's xml in swagger with no model

    suspend fun settings(apiBaseUrl: Url): NetworkResponse<SSOSettingsResponse>
}

class SSOLoginApiImpl(private val httpClient: HttpClient) : SSOLoginApi {
    override suspend fun initiate(param: SSOLoginApi.InitiateParam, apiBaseUrl: Url): NetworkResponse<String> {
        val path = when (param) {
            is SSOLoginApi.InitiateParam.NoRedirect -> "$PATH_SSO/$PATH_INITIATE/${param.code}"
            // ktor will encode the query param as URL so a way around it to append the query to the path string
            is SSOLoginApi.InitiateParam.Redirect -> "$PATH_SSO/$PATH_INITIATE/${param.code}?$QUERY_SUCCESS_REDIRECT=${param.success}&$QUERY_ERROR_REDIRECT=${param.error}"
        }
        return wrapKaliumResponse<Unit> {
            httpClient.head(path) {
                host = apiBaseUrl.host
                url.protocol = URLProtocol.HTTPS
                accept(ContentType.Text.Plain)
            }
        }.mapSuccess {
            "https://${apiBaseUrl.host}/$path"
        }
    }

    override suspend fun finalize(cookie: String, apiBaseUrl: Url): NetworkResponse<String> = wrapKaliumResponse {
        httpClient.post("$PATH_SSO/$PATH_FINALIZE") {
            host = apiBaseUrl.host
            url.protocol = URLProtocol.HTTPS
            header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=$cookie")
        }
    }

    override suspend fun metaData(apiBaseUrl: Url): NetworkResponse<String> = wrapKaliumResponse {
        httpClient.get("$PATH_SSO/$PATH_METADATA") {
            host = apiBaseUrl.host
            url.protocol = URLProtocol.HTTPS
        }
    }

    override suspend fun settings(apiBaseUrl: Url): NetworkResponse<SSOSettingsResponse> = wrapKaliumResponse {
        httpClient.get("$PATH_SSO/$PATH_SETTINGS") {
            host = apiBaseUrl.host
            url.protocol = URLProtocol.HTTPS
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
