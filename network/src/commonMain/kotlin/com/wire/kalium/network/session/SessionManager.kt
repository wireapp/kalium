package com.wire.kalium.network.session

import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.auth.AccessTokenApiImpl
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isInvalidCredentials
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer

interface SessionManager {
    fun session(): Pair<SessionDTO, ServerConfigDTO.Links>
    fun updateSession(newAccessTokenDTO: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?): SessionDTO
    fun onSessionExpired()
}


fun HttpClientConfig<*>.installAuth(sessionManager: SessionManager) {
    install(Auth) {
        bearer {
            // memory cache the tokens
            var access: String
            var refresh: String
            sessionManager.session().first.also { storedSession ->
                access = storedSession.accessToken
                refresh = storedSession.refreshToken
            }

            loadTokens {
                BearerTokens(accessToken = access, refreshToken = refresh)
            }
            refreshTokens {
                when (val response = AccessTokenApiImpl(client).getToken(oldTokens!!.refreshToken)) {
                    is NetworkResponse.Success -> {
                        response.value.first.let { newAccessToken -> access = newAccessToken.value }
                        response.value.second?.let { newRefreshToken -> refresh = newRefreshToken.value }
                        sessionManager.updateSession(response.value.first, response.value.second)
                        BearerTokens(access, refresh)
                    }
                    is NetworkResponse.Error -> {
                        // BE return 403 with error liable invalid-credentials for expired cookies
                        if(response.kException is KaliumException.InvalidRequestError && response.kException.isInvalidCredentials()) {
                            sessionManager.onSessionExpired()
                        }
                        null
                    }
                }
            }
        }
    }
}
