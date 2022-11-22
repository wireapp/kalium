package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.Account
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.feature.auth.AuthTokens
import com.wire.kalium.logic.feature.auth.PersistentWebSocketStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.wrapStorageNullableRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.daokaliumdb.AccountsDAO
import com.wire.kalium.persistence.model.SsoIdEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Suppress("TooManyFunctions")
interface SessionRepository {
    suspend fun storeSession(
        serverConfigId: String,
        ssoId: SsoId?,
        authTokens: AuthTokens,
        proxyCredentials: ProxyCredentials?
    ): Either<StorageFailure, Unit>

    suspend fun allSessions(): Either<StorageFailure, List<AccountInfo>>
    suspend fun allSessionsFlow(): Flow<List<AccountInfo>>
    suspend fun allValidSessions(): Either<StorageFailure, List<AccountInfo.Valid>>
    suspend fun allValidSessionsFlow(): Flow<List<AccountInfo>>
    suspend fun doesValidSessionExist(userId: UserId): Either<StorageFailure, Boolean>
    fun fullAccountInfo(userId: UserId): Either<StorageFailure, Account>
    suspend fun userAccountInfo(userId: UserId): Either<StorageFailure, AccountInfo>
    suspend fun updateCurrentSession(userId: UserId?): Either<StorageFailure, Unit>
    suspend fun logout(userId: UserId, reason: LogoutReason): Either<StorageFailure, Unit>
    fun currentSession(): Either<StorageFailure, AccountInfo>
    fun currentSessionFlow(): Flow<Either<StorageFailure, AccountInfo>>
    suspend fun deleteSession(userId: UserId): Either<StorageFailure, Unit>
    suspend fun ssoId(userId: UserId): Either<StorageFailure, SsoIdEntity?>
    suspend fun updatePersistentWebSocketStatus(userId: UserId, isPersistentWebSocketEnabled: Boolean)
    suspend fun updateSsoId(userId: UserId, ssoId: SsoId?): Either<StorageFailure, Unit>
    fun isFederated(userId: UserId): Either<StorageFailure, Boolean>
    suspend fun getAllValidAccountPersistentWebSocketStatus(): Flow<List<PersistentWebSocketStatus>>
}

@Suppress("TooManyFunctions")
internal class SessionDataSource(
    private val accountsDAO: AccountsDAO,
    private val authTokenStorage: AuthTokenStorage,
    private val serverConfigRepository: ServerConfigRepository,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : SessionRepository {

    override suspend fun storeSession(
        serverConfigId: String,
        ssoId: SsoId?,
        authTokens: AuthTokens,
        proxyCredentials: ProxyCredentials?
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            accountsDAO.insertOrReplace(
                idMapper.toDaoModel(authTokens.userId),
                sessionMapper.toSsoIdEntity(ssoId),
                serverConfigId,
                isPersistentWebSocketEnabled = false
            )
        }.flatMap {
            wrapStorageRequest {
                authTokenStorage.addOrReplace(
                    sessionMapper.toAuthTokensEntity(authTokens),
                    proxyCredentials?.let { sessionMapper.fromModelToProxyCredentialsEntity(it) }
                )
            }
        }

    override suspend fun allSessions(): Either<StorageFailure, List<AccountInfo>> =
        wrapStorageRequest { accountsDAO.allAccountList() }.map { it.map { sessionMapper.fromAccountInfoEntity(it) } }

    override suspend fun allSessionsFlow(): Flow<List<AccountInfo>> =
        accountsDAO.observeAllAccountList()
            .map { it.map { sessionMapper.fromAccountInfoEntity(it) } }

    override suspend fun allValidSessions(): Either<StorageFailure, List<AccountInfo.Valid>> =
        wrapStorageRequest { accountsDAO.allValidAccountList() }
            .map { it.map { AccountInfo.Valid(idMapper.fromDaoModel(it.userIDEntity)) } }

    // TODO: .wrapStorageRequest()
    override suspend fun allValidSessionsFlow(): Flow<List<AccountInfo>> =
        accountsDAO.observerValidAccountList()
            .map { it.map { AccountInfo.Valid(idMapper.fromDaoModel(it.userIDEntity)) } }

    override suspend fun doesValidSessionExist(userId: UserId): Either<StorageFailure, Boolean> =
        wrapStorageRequest { accountsDAO.doesValidAccountExists(idMapper.toDaoModel(userId)) }

    override fun fullAccountInfo(userId: UserId): Either<StorageFailure, Account> =
        wrapStorageRequest { accountsDAO.fullAccountInfo(idMapper.toDaoModel(userId)) }
            .flatMap {
                val accountInfo = sessionMapper.fromAccountInfoEntity(it.info)
                val serverConfig: ServerConfig =
                    serverConfigRepository.configById(it.serverConfigId).fold({ return Either.Left(it) }, { it })
                val ssoId: SsoId? = sessionMapper.fromSsoIdEntity(it.ssoId)
                Either.Right(Account(accountInfo, serverConfig, ssoId))
            }

    override suspend fun userAccountInfo(userId: UserId): Either<StorageFailure, AccountInfo> =
        wrapStorageRequest { accountsDAO.accountInfo(idMapper.toDaoModel(userId)) }
            .map { sessionMapper.fromAccountInfoEntity(it) }

    override suspend fun updateCurrentSession(userId: UserId?): Either<StorageFailure, Unit> =
        wrapStorageRequest { accountsDAO.setCurrentAccount(userId?.let { idMapper.toDaoModel(it) }) }

    override suspend fun logout(
        userId: UserId,
        reason: LogoutReason
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            accountsDAO.markAccountAsInvalid(
                idMapper.toDaoModel(userId),
                sessionMapper.toLogoutReasonEntity(reason)
            )
        }

    override fun currentSession(): Either<StorageFailure, AccountInfo> =
        wrapStorageRequest { accountsDAO.currentAccount() }.map { sessionMapper.fromAccountInfoEntity(it) }

    override fun currentSessionFlow(): Flow<Either<StorageFailure, AccountInfo>> =
        accountsDAO.observerCurrentAccount()
            .map { it?.let { sessionMapper.fromAccountInfoEntity(it) } }
            .wrapStorageRequest()

    override suspend fun deleteSession(userId: UserId): Either<StorageFailure, Unit> {
        val idEntity = idMapper.toDaoModel(userId)
        return wrapStorageRequest { accountsDAO.deleteAccount(idEntity) }
            .onSuccess {
                wrapStorageRequest { authTokenStorage.deleteToken(idEntity) }
            }
    }

    override suspend fun ssoId(userId: UserId): Either<StorageFailure, SsoIdEntity?> =
        wrapStorageNullableRequest { accountsDAO.ssoId(idMapper.toDaoModel(userId)) }

    override suspend fun updatePersistentWebSocketStatus(userId: UserId, isPersistentWebSocketEnabled: Boolean) {
        accountsDAO.updatePersistentWebSocketStatus(idMapper.toDaoModel(userId), isPersistentWebSocketEnabled)
    }

    override suspend fun updateSsoId(userId: UserId, ssoId: SsoId?): Either<StorageFailure, Unit> = wrapStorageRequest {
        accountsDAO.updateSsoId(idMapper.toDaoModel(userId), idMapper.toSsoIdEntity(ssoId))
    }

    override fun isFederated(userId: UserId): Either<StorageFailure, Boolean> = wrapStorageRequest {
        accountsDAO.isFederated(idMapper.toDaoModel(userId))
    }

    override suspend fun getAllValidAccountPersistentWebSocketStatus(): Flow<List<PersistentWebSocketStatus>> =
        accountsDAO.getAllValidAccountPersistentWebSocketStatus().map {
            it.map { persistentWebSocketStatusEntity ->
                sessionMapper.fromPersistentWebSocketStatusEntity(persistentWebSocketStatusEntity)
            }
        }
}
