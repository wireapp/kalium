package com.wire.kalium.logic.network

import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.LogoutUseCase
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
    private val logout: LogoutUseCase
) : SessionManager {
    override fun session(): Pair<SessionDTO, ServerConfigDTO.Links> = sessionRepository.userSession(userId).fold({
        TODO("IMPORTANT! Not yet implemented")
    }, { session ->
        Pair(sessionMapper.toSessionDTO(session), serverConfigMapper.toDTO(session.serverLinks))
    })

    override fun updateSession(newAccessTokenDTO: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?): SessionDTO =
        sessionRepository.userSession(userId).fold({
            TODO("IMPORTANT! Not yet implemented")
        }, { authSession ->
            AuthSession(
                AuthSession.Tokens(
                    authSession.tokens.userId,
                    newAccessTokenDTO.value,
                    newRefreshTokenDTO?.value ?: authSession.tokens.refreshToken,
                    newAccessTokenDTO.tokenType,
                ),
                authSession.serverLinks
            ).let {
                sessionRepository.storeSession(it)
                sessionMapper.toSessionDTO(it)
            }
        })

    override suspend fun onSessionExpired() {
        logout(false)
    }

    override suspend fun onClientRemoved() {
        logout(false)
    }
}
