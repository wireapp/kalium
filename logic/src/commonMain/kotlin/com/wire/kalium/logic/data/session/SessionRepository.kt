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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.Account
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.feature.auth.AuthTokens
import com.wire.kalium.logic.feature.auth.PersistentWebSocketStatus
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.wrapStorageNullableRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.model.ManagedByDTO
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.dao.ManagedByEntity
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
    suspend fun currentSession(): Either<StorageFailure, AccountInfo>
    fun currentSessionFlow(): Flow<Either<StorageFailure, AccountInfo>>
    suspend fun deleteSession(userId: UserId): Either<StorageFailure, Unit>
    suspend fun ssoId(userId: UserId): Either<StorageFailure, SsoIdEntity?>
    suspend fun updatePersistentWebSocketStatus(userId: UserId, isPersistentWebSocketEnabled: Boolean): Either<StorageFailure, Unit>
    suspend fun updateSsoIdAndScimInfo(userId: UserId, ssoId: SsoId?, managedBy: ManagedByDTO?): Either<StorageFailure, Unit>
    fun isFederated(userId: UserId): Either<StorageFailure, Boolean>
    suspend fun getAllValidAccountPersistentWebSocketStatus(): Either<StorageFailure, Flow<List<PersistentWebSocketStatus>>>
    suspend fun persistentWebSocketStatus(userId: UserId): Either<StorageFailure, Boolean>
    suspend fun cookieLabel(userId: UserId): Either<StorageFailure, String?>
    suspend fun isAccountReadOnly(userId: UserId): Either<StorageFailure, Boolean>
}

@Suppress("TooManyFunctions")
internal class SessionDataSource(
    private val accountsDAO: AccountsDAO,
    private val authTokenStorage: AuthTokenStorage,
    private val serverConfigRepository: ServerConfigRepository,
    private val kaliumConfigs: KaliumConfigs,
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
                authTokens.userId.toDao(),
                sessionMapper.toSsoIdEntity(ssoId),
                serverConfigId,
                isPersistentWebSocketEnabled = kaliumConfigs.isWebSocketEnabledByDefault
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
            .map { it.map { AccountInfo.Valid(it.userIDEntity.toModel()) } }

    // TODO: .wrapStorageRequest()
    override suspend fun allValidSessionsFlow(): Flow<List<AccountInfo>> =
        accountsDAO.observerValidAccountList()
            .map { it.map { AccountInfo.Valid(it.userIDEntity.toModel()) } }

    override suspend fun doesValidSessionExist(userId: UserId): Either<StorageFailure, Boolean> =
        wrapStorageRequest { accountsDAO.doesValidAccountExists(userId.toDao()) }

    override fun fullAccountInfo(userId: UserId): Either<StorageFailure, Account> =
        wrapStorageRequest { accountsDAO.fullAccountInfo(userId.toDao()) }
            .flatMap {
                val accountInfo = sessionMapper.fromAccountInfoEntity(it.info)
                val serverConfig: ServerConfig =
                    serverConfigRepository.configById(it.serverConfigId).fold({ return Either.Left(it) }, { it })
                val ssoId: SsoId? = sessionMapper.fromSsoIdEntity(it.ssoId)
                Either.Right(Account(accountInfo, serverConfig, ssoId))
            }

    override suspend fun userAccountInfo(userId: UserId): Either<StorageFailure, AccountInfo> =
        wrapStorageRequest { accountsDAO.accountInfo(userId.toDao()) }
            .map { sessionMapper.fromAccountInfoEntity(it) }

    override suspend fun updateCurrentSession(userId: UserId?): Either<StorageFailure, Unit> =
        wrapStorageRequest { accountsDAO.setCurrentAccount(userId?.toDao()) }

    override suspend fun logout(
        userId: UserId,
        reason: LogoutReason
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            accountsDAO.markAccountAsInvalid(
                userId.toDao(),
                sessionMapper.toLogoutReasonEntity(reason)
            )
        }

    override suspend fun currentSession(): Either<StorageFailure, AccountInfo> =
        wrapStorageRequest { accountsDAO.currentAccount() }.map { sessionMapper.fromAccountInfoEntity(it) }

    override fun currentSessionFlow(): Flow<Either<StorageFailure, AccountInfo>> =
        accountsDAO.observerCurrentAccount()
            .map { it?.let { sessionMapper.fromAccountInfoEntity(it) } }
            .wrapStorageRequest()

    override suspend fun deleteSession(userId: UserId): Either<StorageFailure, Unit> {
        val idEntity = userId.toDao()
        return wrapStorageRequest { accountsDAO.deleteAccount(idEntity) }
            .onSuccess {
                wrapStorageRequest { authTokenStorage.deleteToken(idEntity) }
            }
    }

    override suspend fun ssoId(userId: UserId): Either<StorageFailure, SsoIdEntity?> =
        wrapStorageNullableRequest { accountsDAO.ssoId(userId.toDao()) }

    override suspend fun updatePersistentWebSocketStatus(
        userId: UserId,
        isPersistentWebSocketEnabled: Boolean
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            accountsDAO.updatePersistentWebSocketStatus(userId.toDao(), isPersistentWebSocketEnabled)
        }

    override suspend fun updateSsoIdAndScimInfo(
        userId: UserId,
        ssoId: SsoId?,
        managedBy: ManagedByDTO?
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            accountsDAO.updateSsoIdAndScimInfo(userId.toDao(), idMapper.toSsoIdEntity(ssoId), managedBy?.toDao())
        }

    override fun isFederated(userId: UserId): Either<StorageFailure, Boolean> = wrapStorageRequest {
        accountsDAO.isFederated(userId.toDao())
    }

    override suspend fun getAllValidAccountPersistentWebSocketStatus(): Either<StorageFailure, Flow<List<PersistentWebSocketStatus>>> =
        wrapStorageRequest {
            accountsDAO.getAllValidAccountPersistentWebSocketStatus().map {
                it.map { persistentWebSocketStatusEntity ->
                    sessionMapper.fromPersistentWebSocketStatusEntity(persistentWebSocketStatusEntity)
                }
            }
        }

    override suspend fun persistentWebSocketStatus(userId: UserId): Either<StorageFailure, Boolean> = wrapStorageRequest {
        accountsDAO.persistentWebSocketStatus(userId.toDao())
    }

    override suspend fun cookieLabel(userId: UserId): Either<StorageFailure, String?> = wrapStorageNullableRequest {
        authTokenStorage.getToken(userId.toDao())?.cookieLabel
    }

    override suspend fun isAccountReadOnly(userId: UserId): Either<StorageFailure, Boolean> =
        wrapStorageNullableRequest { accountsDAO.getAccountManagedBy(userId.toDao()) }.map {
            when (it) {
                // Only WIRE and no-value accounts are considered as fully editable
                ManagedByEntity.WIRE, null -> false
                ManagedByEntity.SCIM -> true
            }
        }

    internal fun ManagedByDTO.toDao() = when (this) {
        ManagedByDTO.WIRE -> ManagedByEntity.WIRE
        ManagedByDTO.SCIM -> ManagedByEntity.SCIM
    }
}
