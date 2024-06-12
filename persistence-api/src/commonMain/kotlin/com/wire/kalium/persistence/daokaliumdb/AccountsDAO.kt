/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.persistence.daokaliumdb

import com.wire.kalium.persistence.dao.ManagedByEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.LogoutReason
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.model.SsoIdEntity
import kotlinx.coroutines.flow.Flow

data class AccountInfoEntity(
    val userIDEntity: UserIDEntity,
    val logoutReason: LogoutReason?
)

data class PersistentWebSocketStatusEntity(
    val userIDEntity: UserIDEntity,
    val isPersistentWebSocketEnabled: Boolean
)

data class FullAccountEntity(
    val info: AccountInfoEntity,
    val serverConfigId: String,
    val ssoId: SsoIdEntity?,
    val persistentWebSocketStatusEntity: PersistentWebSocketStatusEntity,
    val managedBy: ManagedByEntity?
)

@Suppress("FunctionParameterNaming", "LongParameterList")
object AccountMapper {
    fun fromAccount(
        user_id: UserIDEntity,
        logout_reason: LogoutReason?,
    ): AccountInfoEntity = AccountInfoEntity(
        userIDEntity = user_id,
        logoutReason = logout_reason,
    )

    fun fromPersistentWebSocketStatus(
        user_id: UserIDEntity,
        isPersistentWebSocketEnabled: Boolean,
    ): PersistentWebSocketStatusEntity = PersistentWebSocketStatusEntity(
        userIDEntity = user_id,
        isPersistentWebSocketEnabled = isPersistentWebSocketEnabled
    )

    fun fromFullAccountInfo(
        id: QualifiedIDEntity,
        scim_external_id: String?,
        subject: String?,
        tenant: String?,
        server_config_id: String,
        logout_reason: LogoutReason?,
        isPersistentWebSocketEnabled: Boolean,
        managedBy: ManagedByEntity?
    ): FullAccountEntity = FullAccountEntity(
        info = fromAccount(id, logout_reason),
        serverConfigId = server_config_id,
        ssoId = toSsoIdEntity(scim_external_id, subject, tenant),
        persistentWebSocketStatusEntity = fromPersistentWebSocketStatus(id, isPersistentWebSocketEnabled),
        managedBy = managedBy
    )

    fun toSsoIdEntity(
        scim_external_id: String?,
        subject: String?,
        tenant: String?
    ): SsoIdEntity? = if (scim_external_id == null && subject == null && tenant == null) {
        null
    } else {
        SsoIdEntity(scim_external_id, subject, tenant)
    }

    fun fromUserIDWithServerConfig(
        userId: QualifiedIDEntity,
        serverConfigId: String,
        apiBaseUrl: String,
        accountBaseUrl: String,
        webSocketBaseUrl: String,
        blackListUrl: String,
        teamsUrl: String,
        websiteUrl: String,
        isOnPremises: Boolean,
        domain: String?,
        commonApiVersion: Int,
        federation: Boolean,
        apiProxyHost: String?,
        apiProxyPort: Int?,
        apiProxyNeedsAuthentication: Boolean?,
        title: String
    ): Pair<UserIDEntity, ServerConfigEntity> {
        val apiProxy: ServerConfigEntity.ApiProxy? =
            if (apiProxyHost != null && apiProxyPort != null && apiProxyNeedsAuthentication != null) {
                ServerConfigEntity.ApiProxy(apiProxyNeedsAuthentication, apiProxyHost, apiProxyPort)
            } else {
                null
            }

        val serverConfig = ServerConfigEntity(
            id = serverConfigId,
            links = ServerConfigEntity.Links(
                api = apiBaseUrl,
                accounts = accountBaseUrl,
                webSocket = webSocketBaseUrl,
                blackList = blackListUrl,
                teams = teamsUrl,
                website = websiteUrl,
                isOnPremises = isOnPremises,
                apiProxy = apiProxy,
                title = title
            ),
            metaData = ServerConfigEntity.MetaData(
                domain = domain,
                apiVersion = commonApiVersion,
                federation = federation,
            )
        )

        return userId to serverConfig
    }

}

@Suppress("TooManyFunctions")
interface AccountsDAO {
    suspend fun ssoId(userIDEntity: UserIDEntity): SsoIdEntity?
    suspend fun insertOrReplace(
        userIDEntity: UserIDEntity,
        ssoIdEntity: SsoIdEntity?,
        serverConfigId: String,
        isPersistentWebSocketEnabled: Boolean
    )

    suspend fun observeAccount(userIDEntity: UserIDEntity): Flow<AccountInfoEntity?>
    suspend fun allAccountList(): List<AccountInfoEntity>
    suspend fun allValidAccountList(): List<AccountInfoEntity>
    fun observerValidAccountList(): Flow<List<AccountInfoEntity>>
    suspend fun observeAllAccountList(): Flow<List<AccountInfoEntity>>
    suspend fun isFederated(userIDEntity: UserIDEntity): Boolean?
    suspend fun doesValidAccountExists(userIDEntity: UserIDEntity): Boolean
    suspend fun currentAccount(): AccountInfoEntity?
    fun observerCurrentAccount(): Flow<AccountInfoEntity?>
    suspend fun setCurrentAccount(userIDEntity: UserIDEntity?)
    suspend fun updateSsoIdAndScimInfo(userIDEntity: UserIDEntity, ssoIdEntity: SsoIdEntity?, managedBy: ManagedByEntity?)
    suspend fun deleteAccount(userIDEntity: UserIDEntity)
    suspend fun markAccountAsInvalid(userIDEntity: UserIDEntity, logoutReason: LogoutReason)
    suspend fun updatePersistentWebSocketStatus(userIDEntity: UserIDEntity, isPersistentWebSocketEnabled: Boolean)
    suspend fun persistentWebSocketStatus(userIDEntity: UserIDEntity): Boolean
    suspend fun accountInfo(userIDEntity: UserIDEntity): AccountInfoEntity?
    fun fullAccountInfo(userIDEntity: UserIDEntity): FullAccountEntity?
    suspend fun getAllValidAccountPersistentWebSocketStatus(): Flow<List<PersistentWebSocketStatusEntity>>
    suspend fun getAccountManagedBy(userIDEntity: UserIDEntity): ManagedByEntity?
    suspend fun validAccountWithServerConfigId(): Map<UserIDEntity, ServerConfigEntity>
}
