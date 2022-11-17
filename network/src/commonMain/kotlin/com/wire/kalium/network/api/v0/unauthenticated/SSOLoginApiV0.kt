package com.wire.kalium.network.api.v0.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.AuthenticationResultDTO
import com.wire.kalium.network.api.base.model.RefreshTokenProperties
import com.wire.kalium.network.api.base.model.UserDTO
import com.wire.kalium.network.api.base.model.toSessionDto
import com.wire.kalium.network.api.base.unauthenticated.SSOLoginApi
import com.wire.kalium.network.api.base.unauthenticated.SSOSettingsResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.splitSetCookieHeader
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.appendPathSegments
import io.ktor.http.parseServerSetCookieHeader

internal open class SSOLoginApiV0 internal constructor(
    private val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : SSOLoginApi {

    private val httpClient get() = unauthenticatedNetworkClient.httpClient

    override suspend fun initiate(param: SSOLoginApi.InitiateParam): NetworkResponse<String> = HttpRequestBuilder().apply {
        url.appendPathSegments(PATH_SSO, PATH_INITIATE, param.uuid)
        if (param is SSOLoginApi.InitiateParam.WithRedirect) {
            parameter(QUERY_SUCCESS_REDIRECT, param.success)
            parameter(QUERY_ERROR_REDIRECT, param.error)
        }
        accept(ContentType.Text.Plain)
    }.let { httpRequestBuilder ->
        val httpRequest = httpClient.head(httpRequestBuilder)
        val url = httpRequest.call.request.url.toString()
        wrapKaliumResponse<Any> { httpRequest }.mapSuccess {
            url
        }
    }

    override suspend fun finalize(cookie: String): NetworkResponse<String> = wrapKaliumResponse {
        httpClient.post("$PATH_SSO/$PATH_FINALIZE") {
            header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=$cookie")
        }
    }

    override suspend fun provideLoginSession(cookie: String): NetworkResponse<AuthenticationResultDTO> =
        wrapKaliumResponse<AccessTokenDTO> {
            httpClient.post(PATH_ACCESS) {
                header(HttpHeaders.Cookie, cookie)
            }
        }.flatMap { accessTokenDTOResponse ->
            val refreshToken = cookie.splitSetCookieHeader().flatMap { it.splitSetCookieHeader() }
                .map { parseServerSetCookieHeader(it) }.associate {
                    it.name to it.value
                }[RefreshTokenProperties.COOKIE_NAME]!!

            with(accessTokenDTOResponse) {
                NetworkResponse.Success(cookie, headers, httpCode)
            }.mapSuccess { Pair(accessTokenDTOResponse.value, it) }
        }.flatMap { tokensPairResponse ->
            wrapKaliumResponse<UserDTO> {
                httpClient.get(PATH_SELF) {
                    bearerAuth(tokensPairResponse.value.first.value)
                }
            }.mapSuccess { userIdDTO ->
                with(tokensPairResponse.value) {
                    AuthenticationResultDTO(first.toSessionDto(second, userIdDTO.id), userIdDTO)
                }
            }
        }

    override suspend fun metaData(): NetworkResponse<String> = wrapKaliumResponse {
        httpClient.get("$PATH_SSO/$PATH_METADATA")
    }

    override suspend fun settings(): NetworkResponse<SSOSettingsResponse> = wrapKaliumResponse {
        httpClient.get("$PATH_SSO/$PATH_SETTINGS")
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
