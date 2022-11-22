package com.wire.kalium.persistence.daokaliumdb

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.AccountsQueries
import com.wire.kalium.persistence.CurrentAccountQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.LogoutReason
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
    val persistentWebSocketStatusEntity: PersistentWebSocketStatusEntity
)

@Suppress("FunctionParameterNaming", "LongParameterList")
internal object AccountMapper {
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
        isPersistentWebSocketEnabled: Boolean
    ): FullAccountEntity = FullAccountEntity(
        info = fromAccount(id, logout_reason),
        serverConfigId = server_config_id,
        ssoId = toSsoIdEntity(scim_external_id, subject, tenant),
        persistentWebSocketStatusEntity = fromPersistentWebSocketStatus(id, isPersistentWebSocketEnabled)
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
    suspend fun observerValidAccountList(): Flow<List<AccountInfoEntity>>
    suspend fun observeAllAccountList(): Flow<List<AccountInfoEntity>>
    fun isFederated(userIDEntity: UserIDEntity): Boolean?
    suspend fun doesValidAccountExists(userIDEntity: UserIDEntity): Boolean
    fun currentAccount(): AccountInfoEntity?
    fun observerCurrentAccount(): Flow<AccountInfoEntity?>
    suspend fun setCurrentAccount(userIDEntity: UserIDEntity?)
    suspend fun updateSsoId(userIDEntity: UserIDEntity, ssoIdEntity: SsoIdEntity?)
    suspend fun deleteAccount(userIDEntity: UserIDEntity)
    suspend fun markAccountAsInvalid(userIDEntity: UserIDEntity, logoutReason: LogoutReason)
    suspend fun updatePersistentWebSocketStatus(userIDEntity: UserIDEntity, isPersistentWebSocketEnabled: Boolean)
    suspend fun accountInfo(userIDEntity: UserIDEntity): AccountInfoEntity?
    fun fullAccountInfo(userIDEntity: UserIDEntity): FullAccountEntity?
    suspend fun getAllValidAccountPersistentWebSocketStatus(): Flow<List<PersistentWebSocketStatusEntity>>
}

@Suppress("TooManyFunctions")
internal class AccountsDAOImpl internal constructor(
    private val queries: AccountsQueries,
    private val currentAccountQueries: CurrentAccountQueries,
    private val mapper: AccountMapper = AccountMapper
) : AccountsDAO {
    override suspend fun ssoId(userIDEntity: UserIDEntity): SsoIdEntity? =
        queries.ssoId(userIDEntity).executeAsOneOrNull()
            ?.let { mapper.toSsoIdEntity(scim_external_id = it.scim_external_id, subject = it.subject, tenant = it.tenant) }

    override suspend fun insertOrReplace(
        userIDEntity: UserIDEntity,
        ssoIdEntity: SsoIdEntity?,
        serverConfigId: String,
        isPersistentWebSocketEnabled: Boolean
    ) {
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
            .mapToOneOrNull()

    override suspend fun allAccountList(): List<AccountInfoEntity> =
        queries.allAccounts(mapper = mapper::fromAccount).executeAsList()

    override suspend fun allValidAccountList(): List<AccountInfoEntity> =
        queries.allValidAccounts(mapper = mapper::fromAccount).executeAsList()

    override suspend fun observerValidAccountList(): Flow<List<AccountInfoEntity>> =
        queries.allValidAccounts(mapper = mapper::fromAccount)
            .asFlow()
            .mapToList()

    override suspend fun observeAllAccountList(): Flow<List<AccountInfoEntity>> =
        queries.allAccounts(mapper = mapper::fromAccount)
            .asFlow()
            .mapToList()

    override fun isFederated(userIDEntity: UserIDEntity): Boolean? =
        queries.isFederationEnabled(userIDEntity).executeAsOneOrNull()

    override suspend fun doesValidAccountExists(userIDEntity: UserIDEntity): Boolean =
        queries.doesValidAccountExist(userIDEntity).executeAsOne()

    override fun currentAccount(): AccountInfoEntity? =
        currentAccountQueries.currentAccountInfo(mapper = mapper::fromAccount).executeAsOneOrNull()

    override fun observerCurrentAccount(): Flow<AccountInfoEntity?> =
        currentAccountQueries.currentAccountInfo(mapper = mapper::fromAccount)
            .asFlow()
            .mapToOneOrNull()

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

    override suspend fun updatePersistentWebSocketStatus(userIDEntity: UserIDEntity, isPersistentWebSocketEnabled: Boolean) {
        queries.updatePersistentWebSocketStatus(isPersistentWebSocketEnabled, userIDEntity)
    }

    override suspend fun getAllValidAccountPersistentWebSocketStatus(): Flow<List<PersistentWebSocketStatusEntity>> =
        queries.allValidAccountsPersistentWebSocketStatus(mapper = mapper::fromPersistentWebSocketStatus).asFlow().mapToList()

    override suspend fun accountInfo(userIDEntity: UserIDEntity): AccountInfoEntity? =
        queries.accountInfo(userIDEntity, mapper = mapper::fromAccount).executeAsOneOrNull()

    override fun fullAccountInfo(userIDEntity: UserIDEntity): FullAccountEntity? =
        queries.fullAccountInfo(userIDEntity, mapper = mapper::fromFullAccountInfo).executeAsOneOrNull()
}
