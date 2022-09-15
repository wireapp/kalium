package com.wire.kalium.logic.network

import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.client.AuthTokenStorage

class SessionManagerImpl(
    private val sessionRepository: SessionRepository,
    private val userId: QualifiedID,
    private val tokenStorage: AuthTokenStorage,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : SessionManager {
    override fun session(): Pair<SessionDTO, ServerConfigDTO.Links> = sessionRepository.fullAccountInfo(userId).fold({
        TODO("IMPORTANT! Not yet implemented")
    }, { account ->
        val session: SessionDTO = wrapStorageRequest { tokenStorage.getToken(idMapper.toDaoModel(account.info.userId)) }
            .map { sessionMapper.fromEntityToSessionDTO(it) }
            .fold({
                throw IllegalStateException("No token found for user")
            }, {
                it
            })
        val links = serverConfigMapper.toDTO(account.serverConfig).links
        session to links
    })

    override fun updateLoginSession(newAccessTokeDTO: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?): SessionDTO =
        wrapStorageRequest {
            tokenStorage.updateToken(
                userId = idMapper.toDaoModel(userId),
                accessToken = newAccessTokeDTO.value,
                tokenType = newAccessTokeDTO.tokenType,
                refreshToken = newRefreshTokenDTO?.value
            )
        }.fold({
            TODO("IMPORTANT! Not yet implemented")
        }, {
            sessionMapper.fromEntityToSessionDTO(it)
        })

    override suspend fun onSessionExpired() {
        sessionRepository.logout(userId, LogoutReason.SESSION_EXPIRED)
    }

    override suspend fun onClientRemoved() {
        sessionRepository.logout(userId, LogoutReason.REMOVED_CLIENT)
    }
}
