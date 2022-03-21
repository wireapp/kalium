package com.wire.kalium.network.session

import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.auth.AccessTokenApiImpl
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.tools.BackendConfig
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer

interface UserSessionManager {
    fun userConfig(): Pair<SessionDTO, BackendConfig>
    fun updateSession(accessToken: AccessTokenDTO, refreshTokenDTO: RefreshTokenDTO?): SessionDTO
}


fun HttpClientConfig<*>.installAuth(userSessionManager: UserSessionManager) {
    install(Auth) {
        bearer {
            val _userSession = userSessionManager.userConfig().first
            var access = _userSession.accessToken
            var refresh = _userSession.refreshToken

            loadTokens {
                BearerTokens(accessToken = access, refreshToken = refresh)
            }
            refreshTokens {
                when (val response = AccessTokenApiImpl(client).getToken(oldTokens!!.refreshToken)) {
                    is NetworkResponse.Success -> {
                        response.value.first.let { newAccessToken -> access = newAccessToken.value }
                        response.value.second?.let { newRefreshToken -> refresh = newRefreshToken.value }
                        userSessionManager.updateSession(response.value.first, response.value.second)
                        BearerTokens(access, refresh)
                    }
                    is NetworkResponse.Error -> TODO()
                }

            }
        }
    }
}
