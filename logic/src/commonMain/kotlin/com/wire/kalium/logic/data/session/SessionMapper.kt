package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.feature.auth.AuthTokens
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.dao_kalium_db.AccountInfoEntity
import com.wire.kalium.persistence.model.SsoIdEntity
import com.wire.kalium.persistence.model.LogoutReason as LogoutReasonEntity

interface SessionMapper {
    fun toSessionDTO(authSession: AuthTokens): SessionDTO
    fun fromEntityToSessionDTO(authTokenEntity: AuthTokenEntity): SessionDTO
    fun fromSessionDTO(sessionDTO: SessionDTO): AuthTokens
    fun fromAccountInfoEntity(accountInfoEntity: AccountInfoEntity): AccountInfo
    fun toLogoutReasonEntity(reason: LogoutReason): LogoutReasonEntity
    fun toSsoIdEntity(ssoId: SsoId?): SsoIdEntity?
    fun toAuthTokensEntity(authSession: AuthTokens): AuthTokenEntity
    fun fromSsoIdEntity(ssoIdEntity: SsoIdEntity?): SsoId?
    fun toLogoutReason(reason: LogoutReasonEntity): LogoutReason
}

internal class SessionMapperImpl(
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

    override fun fromEntityToSessionDTO(authTokenEntity: AuthTokenEntity): SessionDTO = with(authTokenEntity) {
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

    override fun toAuthTokensEntity(authSession: AuthTokens): AuthTokenEntity = AuthTokenEntity(
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
}
