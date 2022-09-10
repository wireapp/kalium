package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.feature.auth.AuthTokens
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.persistence.client.TokenEntity
import com.wire.kalium.persistence.dao_kalium_db.AccountInfoEntity
import com.wire.kalium.persistence.model.SsoIdEntity
import com.wire.kalium.persistence.model.LogoutReason as LogoutReasonEntity

interface SessionMapper {
    fun toSessionDTO(authSession: AuthTokens): SessionDTO
    fun fromEntityToSessionDTO(tokenEntity: TokenEntity): SessionDTO
    fun fromSessionDTO(sessionDTO: SessionDTO): AuthTokens
    fun fromAccountInfoEntity(accountInfoEntity: AccountInfoEntity): AccountInfo
    fun toLogoutReasonEntity(reason: LogoutReason): LogoutReasonEntity
    fun toSsoIdEntity(ssoId: SsoId?): SsoIdEntity?
    fun toAuthTokensEntity(authSession: AuthTokens): TokenEntity
    fun fromSsoIdEntity(ssoIdEntity: SsoIdEntity?): SsoId?
    fun toLogoutReason(reason: LogoutReasonEntity): LogoutReason
    // fun fromPersistenceSession(authSessionEntity: AuthSessionEntity): AuthSession
    // fun toPersistenceSession(authSession: AuthSession, ssoId: SsoId?): AuthSessionEntity
}

internal class SessionMapperImpl(
    private val serverConfigMapper: ServerConfigMapper,
    private val idMapper: IdMapper
) : SessionMapper {

    override fun toSessionDTO(authSession: AuthTokens): SessionDTO = with(authSession) {
        SessionDTO(
            userId = idMapper.toApiModel(userId),
            tokenType = tokenType,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    override fun fromEntityToSessionDTO(tokenEntity: TokenEntity): SessionDTO = with(tokenEntity) {
        SessionDTO(
            userId = idMapper.fromDaoToDto(userId),
            tokenType = tokenType,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    override fun fromSessionDTO(sessionDTO: SessionDTO): AuthTokens = with(sessionDTO) {
        AuthTokens(
            userId = idMapper.fromApiModel(userId),
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = tokenType
        )
    }

    override fun fromAccountInfoEntity(accountInfoEntity: AccountInfoEntity): AccountInfo =
        accountInfoEntity.logoutReason?.let {
            AccountInfo.Invalid(
                idMapper.fromDaoModel(accountInfoEntity.userIDEntity),
                toLogoutReason(it)
            )
        } ?: AccountInfo.Valid(idMapper.fromDaoModel(accountInfoEntity.userIDEntity))


    override fun toLogoutReasonEntity(reason: LogoutReason): LogoutReasonEntity =
        when (reason) {
            LogoutReason.SELF_LOGOUT -> LogoutReasonEntity.SELF_LOGOUT
            LogoutReason.REMOVED_CLIENT -> LogoutReasonEntity.REMOVED_CLIENT
            LogoutReason.DELETED_ACCOUNT -> LogoutReasonEntity.DELETED_ACCOUNT
            LogoutReason.SESSION_EXPIRED -> LogoutReasonEntity.SESSION_EXPIRED
        }

    override fun toSsoIdEntity(ssoId: SsoId?): SsoIdEntity? =
        ssoId?.let { SsoIdEntity(scimExternalId = it.scimExternalId, subject = it.subject, tenant = it.tenant) }

    override fun toAuthTokensEntity(authSession: AuthTokens): TokenEntity = TokenEntity(
        userId = idMapper.toDaoModel(authSession.userId),
        accessToken = authSession.accessToken,
        refreshToken = authSession.refreshToken,
        tokenType = authSession.tokenType
    )

    override fun fromSsoIdEntity(ssoIdEntity: SsoIdEntity?): SsoId? =
        ssoIdEntity?.let { SsoId(scimExternalId = it.scimExternalId, subject = it.subject, tenant = it.tenant) }

    override fun toLogoutReason(reason: com.wire.kalium.persistence.model.LogoutReason): LogoutReason =
        when (reason) {
            LogoutReasonEntity.SELF_LOGOUT -> LogoutReason.SELF_LOGOUT
            LogoutReasonEntity.REMOVED_CLIENT -> LogoutReason.REMOVED_CLIENT
            LogoutReasonEntity.DELETED_ACCOUNT -> LogoutReason.DELETED_ACCOUNT
            LogoutReasonEntity.SESSION_EXPIRED -> LogoutReason.SESSION_EXPIRED
        }

    /*
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
     */
}
