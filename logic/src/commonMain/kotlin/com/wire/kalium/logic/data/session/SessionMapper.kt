package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.persistence.model.AuthSessionEntity

interface SessionMapper {
    fun toSessionDTO(authToken: AuthSession.Token): SessionDTO
    fun fromSessionDTO(sessionDTO: SessionDTO): AuthSession.Token.Valid

    fun fromPersistenceSession(authSessionEntity: AuthSessionEntity): AuthSession
    fun toPersistenceSession(authSession: AuthSession, ssoId: SsoId?): AuthSessionEntity
}

internal class SessionMapperImpl(
    private val serverConfigMapper: ServerConfigMapper,
    private val idMapper: IdMapper
) : SessionMapper {

    override fun toSessionDTO(authToken: AuthSession.Token): SessionDTO = with(authToken) {
        SessionDTO(
            userId = idMapper.toApiModel(userId),
            tokenType = tokenType,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    override fun fromSessionDTO(sessionDTO: SessionDTO): AuthSession.Token.Valid = with(sessionDTO) {
        AuthSession.Token.Valid(idMapper.fromApiModel(userId), accessToken, refreshToken, tokenType)
    }

    override fun fromPersistenceSession(authSessionEntity: AuthSessionEntity): AuthSession =
        when (authSessionEntity) {
            is AuthSessionEntity.Valid -> AuthSession(
                AuthSession.Token.Valid(
                    userId = idMapper.fromDaoModel(authSessionEntity.userId),
                    accessToken = authSessionEntity.accessToken,
                    refreshToken = authSessionEntity.refreshToken,
                    tokenType = authSessionEntity.tokenType,
                ),
                serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
            )

            is AuthSessionEntity.Invalid -> {
                AuthSession(
                    AuthSession.Token.Invalid(
                        userId = idMapper.fromDaoModel(authSessionEntity.userId),
                        reason = LogoutReason.values()[authSessionEntity.reason.ordinal],
                        hardLogout = authSessionEntity.hardLogout,
                        accessToken = authSessionEntity.accessToken,
                        refreshToken = authSessionEntity.refreshToken,
                        tokenType = authSessionEntity.tokenType,
                    ),
                    serverLinks = serverConfigMapper.fromEntity(authSessionEntity.serverLinks)
                )
            }

        }

    override fun toPersistenceSession(authSession: AuthSession, ssoId: SsoId?): AuthSessionEntity =
        when (authSession.token) {
            is AuthSession.Token.Valid -> AuthSessionEntity.Valid(
                userId = idMapper.toDaoModel(authSession.token.userId),
                accessToken = authSession.token.accessToken,
                refreshToken = authSession.token.refreshToken,
                tokenType = authSession.token.tokenType,
                serverLinks = serverConfigMapper.toEntity(authSession.serverLinks),
                ssoId = idMapper.toSsoIdEntity(ssoId)

            )

            is AuthSession.Token.Invalid -> {
                AuthSessionEntity.Invalid(
                    userId = idMapper.toDaoModel(authSession.token.userId),
                    serverLinks = serverConfigMapper.toEntity(authSession.serverLinks),
                    reason = com.wire.kalium.persistence.model.LogoutReason.values()[authSession.token.reason.ordinal],
                    hardLogout = authSession.token.hardLogout,
                    ssoId = idMapper.toSsoIdEntity(ssoId),
                    accessToken = authSession.token.accessToken,
                    refreshToken = authSession.token.refreshToken,
                    tokenType = authSession.token.tokenType,
                )
            }
        }
}
