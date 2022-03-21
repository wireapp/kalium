package com.wire.kalium.logic.network

import com.wire.kalium.logic.configuration.ServerConfigMapper
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.network.api.NonQualifiedUserId
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.session.UserSessionManager
import com.wire.kalium.network.tools.BackendConfig

internal class UserSessionManagerImpl(
    private val sessionRepository: SessionRepository,
    private val userId: NonQualifiedUserId,
    private val sessionMapper: SessionMapper,
    private val serverConfigMapper: ServerConfigMapper
) : UserSessionManager {
    override fun userConfig(): Pair<SessionDTO, BackendConfig> = sessionRepository.userSession(userId).fold({
        TODO()
    }, { session ->
        Pair(sessionMapper.toSessionDTO(session), serverConfigMapper.toBackendConfig(session.serverConfig))
    })

    override fun updateSession(newAccessToken: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?): SessionDTO =
        sessionRepository.userSession(userId).fold({
            TODO()
        }, { authSession ->
            AuthSession(
                authSession.userId,
                newAccessToken.value,
                newRefreshTokenDTO?.value ?: authSession.refreshToken,
                newAccessToken.tokenType,
                authSession.serverConfig
            ).let {
                sessionRepository.storeSession(it)
                sessionMapper.toSessionDTO(it)
            }
        })

    override fun onSessionExpiry() {
        TODO("Not yet implemented")
    }
}
