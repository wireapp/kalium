/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.network.session

import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.HttpResponse
import io.ktor.client.utils.buildHeaders
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.InternalAPI
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.CoroutineContext

interface SessionManager {
    suspend fun session(): SessionDTO?
    fun serverConfig(): ServerConfigDTO

    /**
     * Updates the access token and (possibly) the refresh token for the session.
     *
     * In case of failure to refresh the access token, an exception can be thrown.
     *
     * @param accessTokenApi The AccessTokenApi interface used to retrieve the new access token.
     * @param oldRefreshToken The old refresh token to be replaced.
     * @return The updated SessionDTO object.
     * @see FailureToRefreshTokenException
     */
    suspend fun updateToken(accessTokenApi: AccessTokenApi, oldRefreshToken: String): SessionDTO
    fun proxyCredentials(): ProxyCredentialsDTO?
}

fun HttpClientConfig<*>.installAuth(bearerAuthProvider: BearerAuthProvider) {
    install("Add_WWW-Authenticate_Header") {
        addWWWAuthenticateHeaderIfNeeded()
    }

    install(Auth) {
        providers.add(bearerAuthProvider)
    }
}

@OptIn(InternalAPI::class)
private fun HttpClient.addWWWAuthenticateHeaderIfNeeded() {
    receivePipeline.intercept(HttpReceivePipeline.Before) { response ->
        if (response.status == HttpStatusCode.Unauthorized) {
            val headers = buildHeaders {
                appendAll(response.headers)
                append(HttpHeaders.WWWAuthenticate, "Bearer")
            }
            proceedWith(
                object : HttpResponse() {
                    override val call: HttpClientCall = response.call
                    override val status: HttpStatusCode = response.status
                    override val version: HttpProtocolVersion = response.version
                    override val requestTime: GMTDate = response.requestTime
                    override val responseTime: GMTDate = response.responseTime
                    override val content: ByteReadChannel = response.content
                    override val headers get() = headers
                    override val coroutineContext: CoroutineContext = response.coroutineContext
                }
            )
        }
    }
}

typealias CertificateKey = String
typealias CertificateUrls = List<String>

typealias CertificatePinning = Map<CertificateKey, CertificateUrls>
