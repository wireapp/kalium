package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.persistence.model.AuthSessionEntity

interface SessionMapper {
    fun toSessionDTO(authSession: AuthSession.Session.Valid): SessionDTO
    fun fromSessionDTO(sessionDTO: SessionDTO): AuthSession.Session.Valid

    fun fromPersistenceSession(authSessionEntity: AuthSessionEntity): AuthSession
    fun toPersistenceSession(authSession: AuthSession, ssoId: SsoId?): AuthSessionEntity
}

internal class SessionMapperImpl(
    private val serverConfigMapper: ServerConfigMapper,
    private val idMapper: IdMapper
) : SessionMapper {

    override fun toSessionDTO(authSession: AuthSession.Session.Valid): SessionDTO = with(authSession) {
        SessionDTO(userId = idMapper.toApiModel(userId), tokenType = tokenType, accessToken = accessToken, refreshToken = refreshToken)
    }

    override fun fromSessionDTO(sessionDTO: SessionDTO): AuthSession.Session.Valid = with(sessionDTO) {
        AuthSession.Session.Valid(idMapper.fromApiModel(userId), accessToken, refreshToken, tokenType)
    }

    override fun fromPersistenceSession(authSessionEntity: AuthSessionEntity): AuthSession =
        when (authSessionEntity) {
            is AuthSessionEntity.Valid -> AuthSession(
                AuthSession.Session.Valid(
                    userId = idMapper.fromDaoModel(authSessionEntity.userId),
                    accessToken = authSessionEntity.accessToken,
                    refreshToken = authSessionEntity.refreshToken,
                    tokenType = authSessionEntity.tokenType,
                ),
                serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
            )

            is AuthSessionEntity.Invalid -> {
                AuthSession(
                    AuthSession.Session.Invalid(
                        userId = idMapper.fromDaoModel(authSessionEntity.userId),
                        reason = LogoutReason.values()[authSessionEntity.reason.ordinal],
                        authSessionEntity.hardLogout
                    ),
                    serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
                )
            }

        }

    override fun toPersistenceSession(authSession: AuthSession, ssoId: SsoId?): AuthSessionEntity =
        when (authSession.session) {
            is AuthSession.Session.Valid -> AuthSessionEntity.Valid(
                userId = idMapper.toDaoModel(authSession.session.userId),
                accessToken = authSession.session.accessToken,
                refreshToken = authSession.session.refreshToken,
                tokenType = authSession.session.tokenType,
                serverLinks = serverConfigMapper.toEntity(authSession.serverLinks),
                ssoId = idMapper.toSsoIdEntity(ssoId)

            )

            is AuthSession.Session.Invalid -> {
                AuthSessionEntity.Invalid(
                    userId = idMapper.toDaoModel(authSession.session.userId),
                    serverLinks = serverConfigMapper.toEntity(authSession.serverLinks),
                    reason = com.wire.kalium.persistence.model.LogoutReason.values()[authSession.session.reason.ordinal],
                    hardLogout = authSession.session.hardLogout,
                    ssoId = idMapper.toSsoIdEntity(ssoId)
                )
            }
        }
}
