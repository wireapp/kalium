package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.persistence.model.AuthSessionEntity

interface SessionMapper {
    fun toSessionDTO(authSession: AuthSession.Tokens.Valid): SessionDTO
    fun fromSessionDTO(sessionDTO: SessionDTO): AuthSession.Tokens.Valid

    fun fromPersistenceSession(authSessionEntity: AuthSessionEntity): AuthSession
    fun toPersistenceSession(authSession: AuthSession): AuthSessionEntity
}

internal class SessionMapperImpl(
    private val serverConfigMapper: ServerConfigMapper,
    private val idMapper: IdMapper
) : SessionMapper {

    override fun toSessionDTO(authSession: AuthSession.Tokens.Valid): SessionDTO = with(authSession) {
        SessionDTO(userId = idMapper.toApiModel(userId), tokenType = tokenType, accessToken = accessToken, refreshToken = refreshToken)
    }

    override fun fromSessionDTO(sessionDTO: SessionDTO): AuthSession.Tokens.Valid = with(sessionDTO) {
        AuthSession.Tokens.Valid(idMapper.fromApiModel(userId), accessToken, refreshToken, tokenType)
    }

    override fun fromPersistenceSession(authSessionEntity: AuthSessionEntity): AuthSession =
        when (authSessionEntity) {
            is AuthSessionEntity.ValidSession -> AuthSession(
                AuthSession.Tokens.Valid(
                    userId = idMapper.fromDaoModel(authSessionEntity.userId),
                    accessToken = authSessionEntity.accessToken,
                    refreshToken = authSessionEntity.refreshToken,
                    tokenType = authSessionEntity.tokenType,
                ),
                serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
            )

            is AuthSessionEntity.RemovedClient -> AuthSession(
                AuthSession.Tokens.RemovedClient(userId = idMapper.fromDaoModel(authSessionEntity.userId), false),
                serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
            )

            is AuthSessionEntity.SelfLogout -> AuthSession(
                AuthSession.Tokens.SelfLogout(userId = idMapper.fromDaoModel(authSessionEntity.userId), true),
                serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
            )

            is AuthSessionEntity.UserDeleted -> AuthSession(
                AuthSession.Tokens.UserDeleted(userId = idMapper.fromDaoModel(authSessionEntity.userId), false),
                serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
            )
        }

    override fun toPersistenceSession(authSession: AuthSession): AuthSessionEntity =
        when (authSession.tokens) {
            is AuthSession.Tokens.Valid -> AuthSessionEntity.ValidSession(
                userId = idMapper.toDaoModel(authSession.tokens.userId),
                accessToken = authSession.tokens.accessToken,
                refreshToken = authSession.tokens.refreshToken,
                tokenType = authSession.tokens.tokenType,
                serverLinks = serverConfigMapper.toEntity(authSession.serverLinks)
            )

            is AuthSession.Tokens.RemovedClient -> AuthSessionEntity.RemovedClient(
                userId = idMapper.toDaoModel(authSession.tokens.userId),
                serverLinks = serverConfigMapper.toEntity(authSession.serverLinks),
                hardLogout = authSession.tokens.hardLogout
            )

            is AuthSession.Tokens.UserDeleted -> AuthSessionEntity.UserDeleted(
                userId = idMapper.toDaoModel(authSession.tokens.userId),
                serverLinks = serverConfigMapper.toEntity(authSession.serverLinks),
                hardLogout = authSession.tokens.hardLogout
            )

            is AuthSession.Tokens.SelfLogout -> AuthSessionEntity.SelfLogout(
                userId = idMapper.toDaoModel(authSession.tokens.userId),
                serverLinks = serverConfigMapper.toEntity(authSession.serverLinks),
                hardLogout = authSession.tokens.hardLogout
            )
        }
}
