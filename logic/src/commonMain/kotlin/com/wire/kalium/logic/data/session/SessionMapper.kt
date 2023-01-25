/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.feature.auth.AuthTokens
import com.wire.kalium.logic.feature.auth.PersistentWebSocketStatus
import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.client.ProxyCredentialsEntity
import com.wire.kalium.persistence.daokaliumdb.AccountInfoEntity
import com.wire.kalium.persistence.daokaliumdb.PersistentWebSocketStatusEntity
import com.wire.kalium.persistence.model.SsoIdEntity
import com.wire.kalium.persistence.model.LogoutReason as LogoutReasonEntity

@Suppress("TooManyFunctions")
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
    fun fromEntityToProxyCredentialsDTO(proxyCredentialsEntity: ProxyCredentialsEntity): ProxyCredentialsDTO
    fun fromPersistentWebSocketStatusEntity(
        persistentWebSocketStatusEntity: PersistentWebSocketStatusEntity
    ): PersistentWebSocketStatus
    fun fromModelToProxyCredentialsEntity(proxyCredentialsModel: ProxyCredentials): ProxyCredentialsEntity
    fun fromModelToProxyCredentialsDTO(proxyCredentialsModel: ProxyCredentials): ProxyCredentialsDTO
    fun fromDTOToProxyCredentialsModel(proxyCredentialsDTO: ProxyCredentialsDTO?): ProxyCredentials?
}

@Suppress("TooManyFunctions")
internal class SessionMapperImpl(
    private val idMapper: IdMapper
) : SessionMapper {

    override fun toSessionDTO(authSession: AuthTokens): SessionDTO = with(authSession) {
        SessionDTO(
            userId = userId.toApi(),
            tokenType = tokenType,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    override fun fromEntityToSessionDTO(authTokenEntity: AuthTokenEntity): SessionDTO = with(authTokenEntity) {
        SessionDTO(
            userId = userId.toApi(),
            tokenType = tokenType,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    override fun fromSessionDTO(sessionDTO: SessionDTO): AuthTokens = with(sessionDTO) {
        AuthTokens(
            userId = userId.toModel(),
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = tokenType
        )
    }

    override fun fromAccountInfoEntity(accountInfoEntity: AccountInfoEntity): AccountInfo =
        accountInfoEntity.logoutReason?.let {
            AccountInfo.Invalid(
                accountInfoEntity.userIDEntity.toModel(),
                toLogoutReason(it)
            )
        } ?: AccountInfo.Valid(accountInfoEntity.userIDEntity.toModel())

    override fun toLogoutReasonEntity(reason: LogoutReason): LogoutReasonEntity =
        when (reason) {
            LogoutReason.SELF_HARD_LOGOUT -> LogoutReasonEntity.SELF_HARD_LOGOUT
            LogoutReason.SELF_SOFT_LOGOUT -> LogoutReasonEntity.SELF_SOFT_LOGOUT
            LogoutReason.REMOVED_CLIENT -> LogoutReasonEntity.REMOVED_CLIENT
            LogoutReason.DELETED_ACCOUNT -> LogoutReasonEntity.DELETED_ACCOUNT
            LogoutReason.SESSION_EXPIRED -> LogoutReasonEntity.SESSION_EXPIRED
        }

    override fun toSsoIdEntity(ssoId: SsoId?): SsoIdEntity? =
        ssoId?.let { SsoIdEntity(scimExternalId = it.scimExternalId, subject = it.subject, tenant = it.tenant) }

    override fun toAuthTokensEntity(authSession: AuthTokens): AuthTokenEntity = AuthTokenEntity(
        userId = authSession.userId.toDao(),
        accessToken = authSession.accessToken,
        refreshToken = authSession.refreshToken,
        tokenType = authSession.tokenType
    )

    override fun fromSsoIdEntity(ssoIdEntity: SsoIdEntity?): SsoId? =
        ssoIdEntity?.let { SsoId(scimExternalId = it.scimExternalId, subject = it.subject, tenant = it.tenant) }

    override fun toLogoutReason(reason: com.wire.kalium.persistence.model.LogoutReason): LogoutReason =
        when (reason) {
            LogoutReasonEntity.SELF_SOFT_LOGOUT -> LogoutReason.SELF_SOFT_LOGOUT
            LogoutReasonEntity.SELF_HARD_LOGOUT -> LogoutReason.SELF_HARD_LOGOUT
            LogoutReasonEntity.REMOVED_CLIENT -> LogoutReason.REMOVED_CLIENT
            LogoutReasonEntity.DELETED_ACCOUNT -> LogoutReason.DELETED_ACCOUNT
            LogoutReasonEntity.SESSION_EXPIRED -> LogoutReason.SESSION_EXPIRED
        }

    override fun fromEntityToProxyCredentialsDTO(proxyCredentialsEntity: ProxyCredentialsEntity): ProxyCredentialsDTO =
        ProxyCredentialsDTO(proxyCredentialsEntity.username, proxyCredentialsEntity.password)

    override fun fromPersistentWebSocketStatusEntity(
        persistentWebSocketStatusEntity: PersistentWebSocketStatusEntity
    ): PersistentWebSocketStatus = PersistentWebSocketStatus(
        persistentWebSocketStatusEntity.userIDEntity.toModel(),
        persistentWebSocketStatusEntity.isPersistentWebSocketEnabled
    )
    override fun fromModelToProxyCredentialsEntity(proxyCredentialsModel: ProxyCredentials): ProxyCredentialsEntity =
        ProxyCredentialsEntity(proxyCredentialsModel.username, proxyCredentialsModel.password)

    override fun fromModelToProxyCredentialsDTO(proxyCredentialsModel: ProxyCredentials): ProxyCredentialsDTO =
        ProxyCredentialsDTO(proxyCredentialsModel.username, proxyCredentialsModel.password)

    override fun fromDTOToProxyCredentialsModel(proxyCredentialsDTO: ProxyCredentialsDTO?): ProxyCredentials? =
        proxyCredentialsDTO?.let { (username, password) ->
            if (username != null && password != null) ProxyCredentials(username, password)
            else null
        }

}
