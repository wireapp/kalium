package com.wire.kalium.api

import com.wire.kalium.api.json.model.testCredentials
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.model.RefreshTokenDTO
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.tools.ServerConfigDTO

class TestSessionManagerV0 : SessionManager {
    private val serverConfig = TEST_BACKEND_CONFIG
    private var session = testCredentials

    override suspend fun session(): SessionDTO = session
    override fun serverConfig(): ServerConfigDTO = serverConfig
    override suspend fun updateToken(accessTokenApi: AccessTokenApi, oldAccessToken: String, oldRefreshToken: String): SessionDTO? {
        TODO("Not yet implemented")
    }

    override suspend fun updateLoginSession(newAccessTokenDTO: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?) =
        SessionDTO(
            session.userId,
            newAccessTokenDTO.tokenType,
            newAccessTokenDTO.value,
            newRefreshTokenDTO?.value ?: session.refreshToken
        )

    override fun proxyCredentials(): ProxyCredentialsDTO? =
        ProxyCredentialsDTO("username", "password")


    companion object {
        val SESSION = testCredentials
    }

}
