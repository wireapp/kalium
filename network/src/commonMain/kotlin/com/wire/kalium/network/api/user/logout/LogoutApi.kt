package com.wire.kalium.network.api.user.logout

import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody

interface LogoutApi {
    suspend fun logout(): NetworkResponse<Unit>
    suspend fun removeCookiesByIds(removeCookiesByIdsRequest: RemoveCookiesByIdsRequest): NetworkResponse<Unit>
    suspend fun removeCookiesByLabels(removeCookiesByLabelsRequest: RemoveCookiesByLabels): NetworkResponse<Unit>
}

class LogoutImpl(private val httpClient: HttpClient, private val sessionManager: SessionManager) : LogoutApi {

    override suspend fun logout(): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.post("$PATH_ACCESS/$PATH_LOGOUT") {
            header(HEADER_KEY_COOKIE, "${RefreshTokenProperties.COOKIE_NAME}=${sessionManager.session().first.refreshToken}")
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
        const val HEADER_KEY_COOKIE = "cookie"
        const val PATH_COOKIES = "cookies"
        const val PATH_REMOVE = "remove"
    }
}
