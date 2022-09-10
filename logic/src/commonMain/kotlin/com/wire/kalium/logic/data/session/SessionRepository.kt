package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthTokens
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.dao_kalium_db.AccountsDAO
import com.wire.kalium.persistence.model.SsoIdEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Suppress("TooManyFunctions")
interface SessionRepository {
    suspend fun storeSession(
        userId: UserId,
        serverConfigId: String,
        ssoId: SsoId?,
        authTokens: AuthTokens
    ): Either<StorageFailure, Unit>

    @Deprecated("Use TokenStorage instead")
    suspend fun updateTokens(
        userId: UserId,
        accessTokenDTO: AccessTokenDTO,
        refreshTokenDTO: RefreshTokenDTO?
    ): Either<StorageFailure, AuthSession?>

    // TODO(optimization): exposing all session is unnecessary since we only need the IDs
    //                     of the users getAllSessions(): Either<SessionFailure, List<UserIDs>>
    suspend fun allSessions(): Either<StorageFailure, List<AccountInfo>>
    suspend fun allSessionsFlow(): Flow<List<AccountInfo>>
    suspend fun allValidSessions(): Either<StorageFailure, List<AccountInfo.Valid>>
    suspend fun allValidSessionsFlow(): Flow<List<AccountInfo>>

    // suspend fun userSession(userId: UserId): Either<StorageFailure, AuthSession>
    suspend fun doesSessionExist(userId: UserId): Either<StorageFailure, Boolean>
    suspend fun updateCurrentSession(userId: UserId): Either<StorageFailure, Unit>
    suspend fun logout(userId: UserId, reason: LogoutReason, isHardLogout: Boolean): Either<StorageFailure, Unit>
    fun currentSession(): Either<StorageFailure, UserId>
    fun currentSessionFlow(): Flow<Either<StorageFailure, UserId?>>
    suspend fun deleteSession(userId: UserId): Either<StorageFailure, Unit>
    suspend fun ssoId(userId: UserId): Either<StorageFailure, SsoIdEntity?>
    suspend fun updateSsoId(userId: UserId, ssoId: SsoId?): Either<StorageFailure, Unit>

    fun isFederated(userId: UserId): Either<StorageFailure, Boolean>
}

@Suppress("TooManyFunctions")
internal class SessionDataSource(
    // private val sessionStorage: SessionStorage,
    private val accountsDAO: AccountsDAO,
    private val authTokenStorage: AuthTokenStorage,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : SessionRepository {

    override suspend fun storeSession(
        userId: UserId,
        serverConfigId: String,
        ssoId: SsoId?,
        authTokens: AuthTokens
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            accountsDAO.insertOrReplace(
                idMapper.toDaoModel(userId),
                sessionMapper.toSsoIdEntity(ssoId),
                serverConfigId
            )
        }.flatMap {
            wrapStorageRequest { authTokenStorage.saveToken(sessionMapper.toAuthTokensEntity(authTokens)) }
        }

    override suspend fun updateTokens(
        userId: UserId,
        accessTokenDTO: AccessTokenDTO,
        refreshTokenDTO: RefreshTokenDTO?
    ): Either<StorageFailure, AuthSession?> {
        TODO("deprecated to not use")
    }

    override suspend fun allSessions(): Either<StorageFailure, List<AccountInfo>> =
        wrapStorageRequest { accountsDAO.allAccountList() }.map { it.map { sessionMapper.fromAccountInfoEntity(it) } }

    override suspend fun allSessionsFlow(): Flow<List<AccountInfo>> =
        accountsDAO.observeAllAccountList()
            .map { it.map { sessionMapper.fromAccountInfoEntity(it) } }

    override suspend fun allValidSessions(): Either<StorageFailure, List<AccountInfo.Valid>> =
        wrapStorageRequest { accountsDAO.allValidAccountList() }
            .map { it.map { AccountInfo.Valid(idMapper.fromDaoModel(it.userIDEntity)) } }

    override suspend fun allValidSessionsFlow(): Flow<List<AccountInfo>> =
        accountsDAO.observerValidAccountList()
            .map { it.map { AccountInfo.Valid(idMapper.fromDaoModel(it.userIDEntity)) } }
    //.wrapStorageRequest()

    override suspend fun doesSessionExist(userId: UserId): Either<StorageFailure, Boolean> =
        wrapStorageRequest { accountsDAO.doesAccountExists(idMapper.toDaoModel(userId)) }

    override suspend fun updateCurrentSession(userId: UserId): Either<StorageFailure, Unit> =
        wrapStorageRequest { accountsDAO.setCurrentAccount(idMapper.toDaoModel(userId)) }

    override suspend fun logout(
        userId: UserId,
        reason: LogoutReason,
        isHardLogout: Boolean
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            accountsDAO.markAccountAsInvalid(
                idMapper.toDaoModel(userId),
                sessionMapper.toLogoutReasonEntity(reason)
            )
        }


    override fun currentSession(): Either<StorageFailure, UserId> =
        wrapStorageRequest { accountsDAO.currentAccount() }.map { idMapper.fromDaoModel(it) }

    override fun currentSessionFlow(): Flow<Either<StorageFailure, UserId?>> =
        accountsDAO.observerCurrentAccount().map { it?.let { idMapper.fromDaoModel(it) } }.wrapStorageRequest()

    override suspend fun deleteSession(userId: UserId): Either<StorageFailure, Unit> =
        wrapStorageRequest { accountsDAO.deleteAccount(idMapper.toDaoModel(userId)) }
            .onSuccess {
                TODO("delete acount tokens")
            }

    override suspend fun ssoId(userId: UserId): Either<StorageFailure, SsoIdEntity?> =
        wrapStorageRequest { accountsDAO.ssoId(idMapper.toDaoModel(userId)) }

    override suspend fun updateSsoId(userId: UserId, ssoId: SsoId?): Either<StorageFailure, Unit> = wrapStorageRequest {
        accountsDAO.updateSsoId(idMapper.toDaoModel(userId), idMapper.toSsoIdEntity(ssoId))
    }

    override fun isFederated(userId: UserId): Either<StorageFailure, Boolean> = wrapStorageRequest {
        accountsDAO.isFederated(idMapper.toDaoModel(userId))
    }
}
