package com.wire.kalium.persistence.dao_kalium_db

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.AccountInfo
import com.wire.kalium.persistence.AccountsQueries
import com.wire.kalium.persistence.CurrentAccountQueries
import com.wire.kalium.persistence.client.TokenEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.LogoutReason
import com.wire.kalium.persistence.model.SsoIdEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

sealed class AccountInfoEntity {
    abstract val userIDEntity: UserIDEntity

    data class Valid(override val userIDEntity: UserIDEntity) : AccountInfoEntity()
    data class Invalid(
        override val userIDEntity: UserIDEntity,
        val logoutReason: LogoutReason
    ) : AccountInfoEntity()
}

class AccountsDAO(
    private val queries: AccountsQueries,
    private val currentAccountQueries: CurrentAccountQueries
) {
    suspend fun ssoId(userIDEntity: UserIDEntity): SsoIdEntity? = queries.ssoId(userIDEntity).executeAsOneOrNull()?.let {
        SsoIdEntity(scimExternalId = it.scimExternalId, subject = it.subject, tenant = it.tenant)
    }

    suspend fun insertOrReplace(userIDEntity: UserIDEntity, ssoIdEntity: SsoIdEntity?, serverConfigId: String) {
        queries.insertOrReplace(
            scimExternalId = ssoIdEntity?.scimExternalId,
            subject = ssoIdEntity?.subject,
            tenant = ssoIdEntity?.tenant,
            id = userIDEntity,
            serverConfigId = serverConfigId,
            logoutReason = null
        )
    }

    suspend fun observeAccount(userIDEntity: UserIDEntity): Flow<AccountInfoEntity?> =
        queries.accountInfo(userIDEntity)
            .asFlow()
            .mapToOneOrNull()
            .map { accountInfo ->
                accountInfo?.let {
                    if (it.logoutReason == null) {
                        AccountInfoEntity.Valid(it.id)
                    } else {
                        AccountInfoEntity.Invalid(it.id, it.logoutReason)
                    }
                }
            }


    suspend fun allAccountList(): List<AccountInfoEntity> =
        queries.allAccounts()
            .executeAsList()
            .map { accountInfo ->
                if (accountInfo.logoutReason == null) {
                    AccountInfoEntity.Valid(accountInfo.id)
                } else {
                    AccountInfoEntity.Invalid(accountInfo.id, accountInfo.logoutReason)
                }
            }

    suspend fun allValidAccountList(): List<AccountInfoEntity.Valid> =
        queries.allAccounts()
            .executeAsList()
            .map { accountInfo ->
                AccountInfoEntity.Valid(accountInfo.id)
            }

    suspend fun observerValidAccountList(): Flow<List<AccountInfoEntity.Valid>> =
        queries.allAccounts()
            .asFlow()
            .mapToList()
            .map { accountInfoList ->
                accountInfoList.map { accountInfo ->
                    AccountInfoEntity.Valid(accountInfo.id)
                }
            }

    suspend fun observeAllAccountList(): Flow<List<AccountInfoEntity>> =
        queries.allAccounts()
            .asFlow()
            .mapToList()
            .map { accountInfoList ->
                accountInfoList.map { accountInfo ->
                    if (accountInfo.logoutReason == null) {
                        AccountInfoEntity.Valid(accountInfo.id)
                    } else {
                        AccountInfoEntity.Invalid(accountInfo.id, accountInfo.logoutReason)
                    }
                }
            }


    fun isFederated(userIDEntity: UserIDEntity) = queries.isFederationEnabled(userIDEntity).executeAsOneOrNull()

    suspend fun doesAccountExists(userIDEntity: UserIDEntity): Boolean =
        queries.doesAccountExist(userIDEntity).executeAsOne()


    fun currentAccount(): UserIDEntity? =
        currentAccountQueries.currentAccount().executeAsOneOrNull()?.let { it.user_id }

    fun observerCurrentAccount(): Flow<UserIDEntity?> = currentAccountQueries.currentAccount()
        .asFlow()
        .mapToOneOrNull()
        .map { it?.user_id }
        .distinctUntilChanged()

    suspend fun setCurrentAccount(userIDEntity: UserIDEntity) {
        currentAccountQueries.update(userIDEntity)
    }

    suspend fun updateSsoId(userIDEntity: UserIDEntity, ssoIdEntity: SsoIdEntity?) {
        queries.updateSsoId(
            scimExternalId = ssoIdEntity?.scimExternalId,
            subject = ssoIdEntity?.subject,
            tenant = ssoIdEntity?.tenant,
            userId = userIDEntity
        )
    }

    suspend fun deleteAccount(userIDEntity: UserIDEntity) {
        queries.delete(userIDEntity)
    }

    suspend fun markAccountAsInvalid(userIDEntity: UserIDEntity, logoutReason: LogoutReason) {
        queries.markAccountAsLoggedOut(logoutReason, userIDEntity)
    }
}
