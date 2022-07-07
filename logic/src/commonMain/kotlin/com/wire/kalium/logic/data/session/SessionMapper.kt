package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.persistence.model.AuthSessionEntity

interface SessionMapper {
    fun toSessionDTO(authSession: AuthSession.Session.LoggedIn): SessionDTO
    fun fromSessionDTO(sessionDTO: SessionDTO): AuthSession.Session.LoggedIn

    fun fromPersistenceSession(authSessionEntity: AuthSessionEntity): AuthSession
    fun toPersistenceSession(authSession: AuthSession): AuthSessionEntity
}

internal class SessionMapperImpl(
    private val serverConfigMapper: ServerConfigMapper,
    private val idMapper: IdMapper
) : SessionMapper {

    override fun toSessionDTO(authSession: AuthSession.Session.LoggedIn): SessionDTO = with(authSession) {
        SessionDTO(userId = idMapper.toApiModel(userId), tokenType = tokenType, accessToken = accessToken, refreshToken = refreshToken)
    }

    override fun fromSessionDTO(sessionDTO: SessionDTO): AuthSession.Session.LoggedIn = with(sessionDTO) {
        AuthSession.Session.LoggedIn(idMapper.fromApiModel(userId), accessToken, refreshToken, tokenType)
    }

    override fun fromPersistenceSession(authSessionEntity: AuthSessionEntity): AuthSession =
        when (authSessionEntity) {
            is AuthSessionEntity.LoggedIn -> AuthSession(
                AuthSession.Session.LoggedIn(
                    userId = idMapper.fromDaoModel(authSessionEntity.userId),
                    accessToken = authSessionEntity.accessToken,
                    refreshToken = authSessionEntity.refreshToken,
                    tokenType = authSessionEntity.tokenType,
                ),
                serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
            )

            is AuthSessionEntity.RemovedClient -> AuthSession(
                AuthSession.Session.RemovedClient(userId = idMapper.fromDaoModel(authSessionEntity.userId), false),
                serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
            )

            is AuthSessionEntity.SelfLogout -> AuthSession(
                AuthSession.Session.SelfLogout(userId = idMapper.fromDaoModel(authSessionEntity.userId), true),
                serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
            )

            is AuthSessionEntity.UserDeleted -> AuthSession(
                AuthSession.Session.UserDeleted(userId = idMapper.fromDaoModel(authSessionEntity.userId), false),
                serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
            )
        }

    override fun toPersistenceSession(authSession: AuthSession): AuthSessionEntity =
        when (authSession.session) {
            is AuthSession.Session.LoggedIn -> AuthSessionEntity.LoggedIn(
                userId = idMapper.toDaoModel(authSession.session.userId),
                accessToken = authSession.session.accessToken,
                refreshToken = authSession.session.refreshToken,
                tokenType = authSession.session.tokenType,
                serverLinks = serverConfigMapper.toEntity(authSession.serverLinks)
            )

            is AuthSession.Session.RemovedClient -> AuthSessionEntity.RemovedClient(
                userId = idMapper.toDaoModel(authSession.session.userId),
                serverLinks = serverConfigMapper.toEntity(authSession.serverLinks),
                hardLogout = authSession.session.hardLogout
            )

            is AuthSession.Session.UserDeleted -> AuthSessionEntity.UserDeleted(
                userId = idMapper.toDaoModel(authSession.session.userId),
                serverLinks = serverConfigMapper.toEntity(authSession.serverLinks),
                hardLogout = authSession.session.hardLogout
            )

            is AuthSession.Session.SelfLogout -> AuthSessionEntity.SelfLogout(
                userId = idMapper.toDaoModel(authSession.session.userId),
                serverLinks = serverConfigMapper.toEntity(authSession.serverLinks),
                hardLogout = authSession.session.hardLogout
            )
        }
}
