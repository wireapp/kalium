package com.wire.kalium.logic.network

import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.tools.ServerConfigDTO

class SessionManagerImpl(
    private val sessionRepository: SessionRepository,
    private val userId: QualifiedID,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper()
) : SessionManager {
    override fun session(): Pair<SessionDTO, ServerConfigDTO> = sessionRepository.userSession(userId).fold({
        TODO("no session is stored to the user")
    }, { session ->
        Pair(sessionMapper.toSessionDTO(session), serverConfigMapper.toDTO(session.serverConfig))
    })

    override fun updateSession(newAccessTokenDTO: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?): SessionDTO =
        sessionRepository.userSession(userId).fold({
            TODO("no session is stored to the user")
        }, { authSession ->
            AuthSession(
                authSession.userId,
                newAccessTokenDTO.value,
                newRefreshTokenDTO?.value ?: authSession.refreshToken,
                newAccessTokenDTO.tokenType,
                authSession.serverConfig
            ).let {
                sessionRepository.storeSession(it)
                sessionMapper.toSessionDTO(it)
            }
        })

    override fun onSessionExpired() {
        TODO("Not yet implemented")
    }
}
