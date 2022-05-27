package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.persistence.model.AuthSessionEntity

interface SessionMapper {
    fun toSessionDTO(authSession: AuthSession): SessionDTO
    fun fromSessionDTO(sessionDTO: SessionDTO): AuthSession.Tokens

    fun fromPersistenceSession(authSessionEntity: AuthSessionEntity): AuthSession
    fun toPersistenceSession(authSession: AuthSession): AuthSessionEntity
}

internal class SessionMapperImpl(
    private val serverConfigMapper: ServerConfigMapper,
    private val idMapper: IdMapper
) : SessionMapper {

    override fun toSessionDTO(authSession: AuthSession): SessionDTO = with(authSession.tokens) {
        SessionDTO(userId = idMapper.toApiModel(userId), tokenType = tokenType, accessToken = accessToken, refreshToken = refreshToken)
    }

    override fun fromSessionDTO(sessionDTO: SessionDTO): AuthSession.Tokens = with(sessionDTO) {
            AuthSession.Tokens(idMapper.fromApiModel(userId), accessToken, refreshToken, tokenType)
    }

    override fun fromPersistenceSession(authSessionEntity: AuthSessionEntity): AuthSession = AuthSession(
        AuthSession.Tokens(
            userId = idMapper.fromDaoModel(authSessionEntity.userId),
            accessToken = authSessionEntity.accessToken,
            refreshToken = authSessionEntity.refreshToken,
            tokenType = authSessionEntity.tokenType,
        ),
        serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
    )

    override fun toPersistenceSession(authSession: AuthSession): AuthSessionEntity = with(authSession) {
        AuthSessionEntity(
            userId = idMapper.toDaoModel(tokens.userId),
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            tokenType = tokens.tokenType,
            serverLinks = serverConfigMapper.toEntity(serverLinks)
        )
    }
}
