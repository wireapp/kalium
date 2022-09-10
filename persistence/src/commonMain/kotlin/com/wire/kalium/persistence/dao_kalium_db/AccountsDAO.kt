package com.wire.kalium.persistence.dao_kalium_db

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.AccountInfo
import com.wire.kalium.persistence.AccountsQueries
import com.wire.kalium.persistence.AllAccounts
import com.wire.kalium.persistence.CurrentAccountQueries
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.LogoutReason
import com.wire.kalium.persistence.model.SsoIdEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class AccountInfoEntity(
    val userIDEntity: UserIDEntity,
    val logoutReason: LogoutReason?
) {
    internal constructor(accountInfo: AccountInfo) : this(
        userIDEntity = accountInfo.id,
        logoutReason = accountInfo.logoutReason
    )

    internal constructor(allAccounts: AllAccounts) : this(
        userIDEntity = allAccounts.id,
        logoutReason = allAccounts.logoutReason
    )
}

data class FullAccountEntity(
    val info: AccountInfoEntity,
    val serverConfigId: String,
    val ssoId: SsoIdEntity?,
    val logoutReason: LogoutReason?
)

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
                accountInfo?.let { AccountInfoEntity(accountInfo) }
            }


    suspend fun allAccountList(): List<AccountInfoEntity> =
        queries.allAccounts()
            .executeAsList()
            .map { accountInfo ->
                AccountInfoEntity(accountInfo)
            }

    suspend fun allValidAccountList(): List<AccountInfoEntity> =
        queries.allValidAccounts()
            .executeAsList()
            .map { validAccount ->
                AccountInfoEntity(validAccount.id, validAccount.logoutReason)
            }

    suspend fun observerValidAccountList(): Flow<List<AccountInfoEntity>> =
        queries.allAccounts()
            .asFlow()
            .mapToList()
            .map { accountInfoList ->
                accountInfoList.map { accountInfo ->
                    AccountInfoEntity(accountInfo.id, null)
                }
            }

    suspend fun observeAllAccountList(): Flow<List<AccountInfoEntity>> =
        queries.allAccounts()
            .asFlow()
            .mapToList()
            .map { accountInfoList ->
                accountInfoList.map { AccountInfoEntity(it) }
            }


    fun isFederated(userIDEntity: UserIDEntity) = queries.isFederationEnabled(userIDEntity).executeAsOneOrNull()

    suspend fun doesAccountExists(userIDEntity: UserIDEntity): Boolean =
        queries.doesAccountExist(userIDEntity).executeAsOne()

    suspend fun doesValidAccountExists(userIDEntity: UserIDEntity): Boolean =
        queries.doesValidAccountExist(userIDEntity).executeAsOne()


    fun currentAccount(): UserIDEntity? =
        currentAccountQueries.currentUserId().executeAsOneOrNull()?.let { it.user_id }

    fun observerCurrentAccount(): Flow<UserIDEntity?> = currentAccountQueries.currentUserId()
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

    suspend fun accountInfo(userIDEntity: UserIDEntity): AccountInfoEntity? =
        queries.accountInfo(userIDEntity).executeAsOneOrNull()?.let { AccountInfoEntity(it.id, it.logoutReason) }

    fun fullAccountInfo(userIDEntity: UserIDEntity): FullAccountEntity? =
        queries.fullAccountInfo(userIDEntity).executeAsOneOrNull()?.let {
            FullAccountEntity(
                info = AccountInfoEntity(it.id, it.logoutReason),
                serverConfigId = it.serverConfigId,
                ssoId = it.scimExternalId?.let { scimExternalId ->
                    SsoIdEntity(
                        scimExternalId = scimExternalId,
                        subject = it.subject,
                        tenant = it.tenant
                    )
                },
                logoutReason = it.logoutReason
            )
        }
}
