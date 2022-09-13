package com.wire.kalium.persistence.daoKaliumDB

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

@Suppress("TooManyFunctions")
interface AccountsDAO {
    suspend fun ssoId(userIDEntity: UserIDEntity): SsoIdEntity?
    suspend fun insertOrReplace(userIDEntity: UserIDEntity, ssoIdEntity: SsoIdEntity?, serverConfigId: String)
    suspend fun observeAccount(userIDEntity: UserIDEntity): Flow<AccountInfoEntity?>
    suspend fun allAccountList(): List<AccountInfoEntity>
    suspend fun allValidAccountList(): List<AccountInfoEntity>
    suspend fun observerValidAccountList(): Flow<List<AccountInfoEntity>>
    suspend fun observeAllAccountList(): Flow<List<AccountInfoEntity>>
    fun isFederated(userIDEntity: UserIDEntity): Boolean?
    suspend fun doesAccountExists(userIDEntity: UserIDEntity): Boolean
    suspend fun doesValidAccountExists(userIDEntity: UserIDEntity): Boolean
    fun currentAccount(): AccountInfoEntity?
    fun observerCurrentAccount(): Flow<AccountInfoEntity?>
    suspend fun setCurrentAccount(userIDEntity: UserIDEntity?)
    suspend fun updateSsoId(userIDEntity: UserIDEntity, ssoIdEntity: SsoIdEntity?)
    suspend fun deleteAccount(userIDEntity: UserIDEntity)
    suspend fun markAccountAsInvalid(userIDEntity: UserIDEntity, logoutReason: LogoutReason)
    suspend fun accountInfo(userIDEntity: UserIDEntity): AccountInfoEntity?
    fun fullAccountInfo(userIDEntity: UserIDEntity): FullAccountEntity?
}

@Suppress("TooManyFunctions")
internal class AccountsDAOImpl internal constructor(
    private val queries: AccountsQueries,
    private val currentAccountQueries: CurrentAccountQueries
) : AccountsDAO {
    override suspend fun ssoId(userIDEntity: UserIDEntity): SsoIdEntity? = queries.ssoId(userIDEntity).executeAsOneOrNull()?.let {
        SsoIdEntity(scimExternalId = it.scimExternalId, subject = it.subject, tenant = it.tenant)
    }

    override suspend fun insertOrReplace(userIDEntity: UserIDEntity, ssoIdEntity: SsoIdEntity?, serverConfigId: String) {
        queries.insertOrReplace(
            scimExternalId = ssoIdEntity?.scimExternalId,
            subject = ssoIdEntity?.subject,
            tenant = ssoIdEntity?.tenant,
            id = userIDEntity,
            serverConfigId = serverConfigId,
            logoutReason = null
        )
    }

    override suspend fun observeAccount(userIDEntity: UserIDEntity): Flow<AccountInfoEntity?> =
        queries.accountInfo(userIDEntity)
            .asFlow()
            .mapToOneOrNull()
            .map { accountInfo ->
                accountInfo?.let { AccountInfoEntity(accountInfo) }
            }

    override suspend fun allAccountList(): List<AccountInfoEntity> =
        queries.allAccounts()
            .executeAsList()
            .map { accountInfo ->
                AccountInfoEntity(accountInfo)
            }

    override suspend fun allValidAccountList(): List<AccountInfoEntity> =
        queries.allValidAccounts()
            .executeAsList()
            .map { validAccount ->
                AccountInfoEntity(validAccount.id, validAccount.logoutReason)
            }

    override suspend fun observerValidAccountList(): Flow<List<AccountInfoEntity>> =
        queries.allAccounts()
            .asFlow()
            .mapToList()
            .map { accountInfoList ->
                accountInfoList.map { accountInfo ->
                    AccountInfoEntity(accountInfo.id, null)
                }
            }

    override suspend fun observeAllAccountList(): Flow<List<AccountInfoEntity>> =
        queries.allAccounts()
            .asFlow()
            .mapToList()
            .map { accountInfoList ->
                accountInfoList.map { AccountInfoEntity(it) }
            }

    override fun isFederated(userIDEntity: UserIDEntity): Boolean? = queries.isFederationEnabled(userIDEntity).executeAsOneOrNull()

    override suspend fun doesAccountExists(userIDEntity: UserIDEntity): Boolean =
        queries.doesAccountExist(userIDEntity).executeAsOne()

    override suspend fun doesValidAccountExists(userIDEntity: UserIDEntity): Boolean =
        queries.doesValidAccountExist(userIDEntity).executeAsOne()

    override fun currentAccount(): AccountInfoEntity? =
        currentAccountQueries.currentAccountInfo().executeAsOneOrNull()?.let { AccountInfoEntity(it.id, it.logoutReason) }

    override fun observerCurrentAccount(): Flow<AccountInfoEntity?> = currentAccountQueries.currentAccountInfo()
        .asFlow()
        .mapToOneOrNull()
        .map { it?.let { AccountInfoEntity(it.id, it.logoutReason) } }
        .distinctUntilChanged()

    override suspend fun setCurrentAccount(userIDEntity: UserIDEntity?) {
        currentAccountQueries.update(userIDEntity)
    }

    override suspend fun updateSsoId(userIDEntity: UserIDEntity, ssoIdEntity: SsoIdEntity?) {
        queries.updateSsoId(
            scimExternalId = ssoIdEntity?.scimExternalId,
            subject = ssoIdEntity?.subject,
            tenant = ssoIdEntity?.tenant,
            userId = userIDEntity
        )
    }

    override suspend fun deleteAccount(userIDEntity: UserIDEntity) {
        queries.delete(userIDEntity)
    }

    override suspend fun markAccountAsInvalid(userIDEntity: UserIDEntity, logoutReason: LogoutReason) {
        queries.markAccountAsLoggedOut(logoutReason, userIDEntity)
    }

    override suspend fun accountInfo(userIDEntity: UserIDEntity): AccountInfoEntity? =
        queries.accountInfo(userIDEntity).executeAsOneOrNull()?.let { AccountInfoEntity(it.id, it.logoutReason) }

    override fun fullAccountInfo(userIDEntity: UserIDEntity): FullAccountEntity? =
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
