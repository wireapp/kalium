package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.configuration.ServerConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.persistence.model.PersistenceSession

interface SessionMapper {
    fun toSessionDTO(authSession: AuthSession): SessionDTO
    fun fromSessionDTO(sessionDTO: SessionDTO, serverConfig: ServerConfig): AuthSession

    fun fromPersistenceSession(persistenceSession: PersistenceSession): AuthSession
    fun toPersistenceSession(authSession: AuthSession): PersistenceSession
}

internal class SessionMapperImpl(
    private val serverConfigMapper: ServerConfigMapper,
    private val idMapper: IdMapper
) : SessionMapper {

    override fun toSessionDTO(authSession: AuthSession): SessionDTO = with(authSession) {
        SessionDTO(userId = idMapper.toApiModel(userId), tokenType = tokenType, accessToken = accessToken, refreshToken = refreshToken)
    }

    override fun fromSessionDTO(sessionDTO: SessionDTO, serverConfig: ServerConfig): AuthSession = with(sessionDTO) {
        AuthSession(idMapper.fromApiModel(userId), accessToken, refreshToken, tokenType, serverConfig)
    }

    override fun fromPersistenceSession(persistenceSession: PersistenceSession): AuthSession = AuthSession(
        userId = idMapper.fromDaoModel(persistenceSession.userId),
        accessToken = persistenceSession.accessToken,
        refreshToken = persistenceSession.refreshToken,
        tokenType = persistenceSession.tokenType,
        serverConfig = serverConfigMapper.fromNetworkConfig(persistenceSession.networkConfig)
    )

    override fun toPersistenceSession(authSession: AuthSession): PersistenceSession = PersistenceSession(
        userId = idMapper.toDaoModel(authSession.userId),
        accessToken = authSession.accessToken,
        refreshToken = authSession.refreshToken,
        tokenType = authSession.tokenType,
        networkConfig = serverConfigMapper.toNetworkConfig(authSession.serverConfig)
    )

}
