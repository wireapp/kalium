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

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.AccountsQueries
import com.wire.kalium.persistence.CurrentAccountQueries
import com.wire.kalium.persistence.dao.ManagedByEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.LogoutReason
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.model.SsoIdEntity
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@Suppress("TooManyFunctions")
internal class AccountsDAOImpl internal constructor(
    private val queries: AccountsQueries,
    private val currentAccountQueries: CurrentAccountQueries,
    private val queriesContext: CoroutineContext,
    private val mapper: AccountMapper = AccountMapper
) : AccountsDAO {
    override suspend fun ssoId(userIDEntity: UserIDEntity): SsoIdEntity? = withContext(queriesContext) {
        queries.ssoId(userIDEntity).executeAsOneOrNull()
            ?.let {
                mapper.toSsoIdEntity(
                    scim_external_id = it.scim_external_id,
                    subject = it.subject,
                    tenant = it.tenant
                )
            }
    }

    override suspend fun insertOrReplace(
        userIDEntity: UserIDEntity,
        ssoIdEntity: SsoIdEntity?,
        serverConfigId: String,
        isPersistentWebSocketEnabled: Boolean
    ) = withContext(queriesContext) {
        queries.insertOrReplace(
            scimExternalId = ssoIdEntity?.scimExternalId,
            subject = ssoIdEntity?.subject,
            tenant = ssoIdEntity?.tenant,
            id = userIDEntity,
            serverConfigId = serverConfigId,
            logoutReason = null,
            isPersistentWebSocketEnabled = isPersistentWebSocketEnabled
        )
    }

    override suspend fun observeAccount(userIDEntity: UserIDEntity): Flow<AccountInfoEntity?> =
        queries.accountInfo(userIDEntity, mapper = mapper::fromAccount)
            .asFlow()
            .flowOn(queriesContext)
            .mapToOneOrNull()

    override suspend fun allAccountList(): List<AccountInfoEntity> = withContext(queriesContext) {
        queries.allAccounts(mapper = mapper::fromAccount).executeAsList()
    }

    override suspend fun allValidAccountList(): List<AccountInfoEntity> = withContext(queriesContext) {
        queries.allValidAccounts(mapper = mapper::fromAccount).executeAsList()
    }

    override fun observerValidAccountList(): Flow<List<AccountInfoEntity>> =
        queries.allValidAccounts(mapper = mapper::fromAccount)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()

    override suspend fun observeAllAccountList(): Flow<List<AccountInfoEntity>> =
        queries.allAccounts(mapper = mapper::fromAccount)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()

    override suspend fun isFederated(userIDEntity: UserIDEntity): Boolean? =
        withContext(queriesContext) {
            queries.isFederationEnabled(userIDEntity).executeAsOneOrNull()
        }

    override suspend fun doesValidAccountExists(userIDEntity: UserIDEntity): Boolean = withContext(queriesContext) {
        queries.doesValidAccountExist(userIDEntity).executeAsOne()
    }

    override suspend fun currentAccount(): AccountInfoEntity? = withContext(queriesContext) {
        currentAccountQueries.currentAccountInfo(mapper = mapper::fromAccount).executeAsOneOrNull()
    }

    override fun observerCurrentAccount(): Flow<AccountInfoEntity?> =
        currentAccountQueries.currentAccountInfo(mapper = mapper::fromAccount)
            .asFlow()
            .flowOn(queriesContext)
            .mapToOneOrNull()

    override suspend fun setCurrentAccount(userIDEntity: UserIDEntity?) = withContext(queriesContext) {
        currentAccountQueries.update(userIDEntity)
    }

    override suspend fun updateSsoIdAndScimInfo(userIDEntity: UserIDEntity, ssoIdEntity: SsoIdEntity?, managedBy: ManagedByEntity?) =
        withContext(queriesContext) {
            queries.transaction {
                queries.updateSsoId(
                    scimExternalId = ssoIdEntity?.scimExternalId,
                    subject = ssoIdEntity?.subject,
                    tenant = ssoIdEntity?.tenant,
                    userId = userIDEntity
                )

                queries.updateManagedBy(managedBy, userIDEntity)
            }
        }

    override suspend fun deleteAccount(userIDEntity: UserIDEntity) =
        withContext(queriesContext) {
            queries.delete(userIDEntity)
        }

    override suspend fun markAccountAsInvalid(userIDEntity: UserIDEntity, logoutReason: LogoutReason) =
        withContext(queriesContext) {
            queries.markAccountAsLoggedOut(logoutReason, userIDEntity)
        }

    override suspend fun updatePersistentWebSocketStatus(userIDEntity: UserIDEntity, isPersistentWebSocketEnabled: Boolean) =
        withContext(queriesContext) {
            queries.updatePersistentWebSocketStatus(isPersistentWebSocketEnabled, userIDEntity)
        }

    override suspend fun persistentWebSocketStatus(userIDEntity: UserIDEntity): Boolean = withContext(queriesContext) {
        queries.persistentWebSocketStatus(userIDEntity).executeAsOne()
    }

    override suspend fun getAllValidAccountPersistentWebSocketStatus(): Flow<List<PersistentWebSocketStatusEntity>> =
        queries.allValidAccountsPersistentWebSocketStatus(mapper = mapper::fromPersistentWebSocketStatus).asFlow().flowOn(queriesContext)
            .mapToList()

    override suspend fun getAccountManagedBy(userIDEntity: UserIDEntity): ManagedByEntity? = withContext(queriesContext) {
        queries.managedBy(userIDEntity).executeAsOneOrNull()?.managed_by
    }

    override suspend fun validAccountWithServerConfigId(): Map<UserIDEntity, ServerConfigEntity> = withContext(queriesContext) {
        queries.allValidAccountsWithServerConfig(mapper = mapper::fromUserIDWithServerConfig).executeAsList().toMap()
    }

    override suspend fun accountInfo(userIDEntity: UserIDEntity): AccountInfoEntity? = withContext(queriesContext) {
        queries.accountInfo(userIDEntity, mapper = mapper::fromAccount).executeAsOneOrNull()
    }

    override fun fullAccountInfo(userIDEntity: UserIDEntity): FullAccountEntity? =
        queries.fullAccountInfo(userIDEntity, mapper = mapper::fromFullAccountInfo).executeAsOneOrNull()
}
