package com.wire.kalium.logic.network

import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
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
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper(),
) : SessionManager {
    override fun session(): Pair<SessionDTO, ServerConfigDTO.Links> = sessionRepository.userSession(userId).fold({
        TODO("IMPORTANT! Not yet implemented")
    }, { session ->
        Pair(sessionMapper.toSessionDTO(session.token), serverConfigMapper.toDTO(session.serverLinks))
    })

    override fun updateLoginSession(newAccessTokeDTO: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?): SessionDTO =
        sessionRepository.updateTokens(userId, newAccessTokeDTO, newRefreshTokenDTO).fold({
            TODO("IMPORTANT! Not yet implemented")
        }, {
            // TODO: make the function return null when the update fails and delete the type casting
            sessionMapper.toSessionDTO(it?.token as AuthSession.Token.Valid)
        })

    override suspend fun onSessionExpired() {
        sessionRepository.logout(userId, LogoutReason.SESSION_EXPIRED, false)
    }

    override suspend fun onClientRemoved() {
        sessionRepository.logout(userId, LogoutReason.REMOVED_CLIENT, false)
    }
}
