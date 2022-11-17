package com.wire.kalium.network.session

import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.model.RefreshTokenDTO
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isUnknownClient
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
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
    fun session(): SessionDTO

    fun serverConfig(): ServerConfigDTO
    fun updateLoginSession(
        newAccessTokenDTO: AccessTokenDTO,
        newRefreshTokenDTO: RefreshTokenDTO?
    ): SessionDTO

    suspend fun onSessionExpired()
    suspend fun onClientRemoved()
    fun proxyCredentials(): ProxyCredentialsDTO?
}

fun HttpClientConfig<*>.installAuth(sessionManager: SessionManager, accessTokenApi: (httpClient: HttpClient) -> AccessTokenApi) {
    install("Add_WWW-Authenticate_Header") {
        addWWWAuthenticateHeaderIfNeeded()
    }
    install(Auth) {
        bearer {

            loadTokens {
                val session = sessionManager.session()
                BearerTokens(accessToken = session.accessToken, refreshToken = session.refreshToken)
            }

            refreshTokens {
                when (val response = accessTokenApi(client).getToken(oldTokens!!.refreshToken)) {
                    is NetworkResponse.Success -> {
                        val newSession = sessionManager.updateLoginSession(response.value.first, response.value.second)
                        BearerTokens(newSession.accessToken, newSession.refreshToken)
                    }

                    is NetworkResponse.Error -> {
                        // BE return 403 with error liable invalid-credentials for expired cookies
                        if (response.kException is KaliumException.InvalidRequestError) {
                            if (response.kException.errorResponse.code == HttpStatusCode.Forbidden.value)
                                sessionManager.onSessionExpired()
                            if (response.kException.isUnknownClient())
                                sessionManager.onClientRemoved()
                        }
                        null
                    }
                }.also {
                    kaliumLogger.d("AUTH TOKEN REFRESH:{\"status\": ${response.status.value}}")
                }
            }
        }
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
