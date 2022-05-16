package com.wire.kalium.network.api.user.logout

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders

interface LogoutApi {
    suspend fun logout(): NetworkResponse<Unit>
    suspend fun removeCookiesByIds(removeCookiesByIdsRequest: RemoveCookiesByIdsRequest): NetworkResponse<Unit>
    suspend fun removeCookiesByLabels(removeCookiesByLabelsRequest: RemoveCookiesByLabels): NetworkResponse<Unit>
}

class LogoutImpl internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val sessionManager: SessionManager
) : LogoutApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun logout(): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.post("$PATH_ACCESS/$PATH_LOGOUT") {
            header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=${sessionManager.session().first.refreshToken}")
        }
    }

    override suspend fun removeCookiesByIds(removeCookiesByIdsRequest: RemoveCookiesByIdsRequest): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post("$PATH_COOKIES/$PATH_REMOVE") {
                setBody(removeCookiesByIdsRequest)
            }
        }

    override suspend fun removeCookiesByLabels(removeCookiesByLabelsRequest: RemoveCookiesByLabels): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post("$PATH_COOKIES/$PATH_REMOVE") {
                setBody(removeCookiesByLabelsRequest)
            }
        }

    private companion object {
        const val PATH_ACCESS = "access"
        const val PATH_LOGOUT = "logout"
        const val PATH_COOKIES = "cookies"
        const val PATH_REMOVE = "remove"
    }
}
