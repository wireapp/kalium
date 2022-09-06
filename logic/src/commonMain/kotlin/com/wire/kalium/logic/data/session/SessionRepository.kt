package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.persistence.client.SessionStorage
import com.wire.kalium.persistence.model.AuthSessionEntity
import com.wire.kalium.persistence.model.SsoIdEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
@Suppress("TooManyFunctions")
interface SessionRepository {
    fun storeSession(autSession: AuthSession, ssoId: SsoId?): Either<StorageFailure, Unit>
    fun updateTokens(
        userId: UserId,
        accessTokenDTO: AccessTokenDTO,
        refreshTokenDTO: RefreshTokenDTO?
    ): Either<StorageFailure, AuthSession?>

    // TODO(optimization): exposing all session is unnecessary since we only need the IDs
    //                     of the users getAllSessions(): Either<SessionFailure, List<UserIDs>>
    fun allSessions(): Either<StorageFailure, List<AuthSession>>
    fun allSessionsFlow(): Flow<List<AuthSession>>
    fun allValidSessions(): Either<StorageFailure, List<AuthSession>>
    fun allValidSessionsFlow(): Flow<List<AuthSession>>
    fun userSession(userId: UserId): Either<StorageFailure, AuthSession>
    fun doesSessionExist(userId: UserId): Either<StorageFailure, Boolean>
    fun updateCurrentSession(userId: UserId?): Either<StorageFailure, Unit>
    fun logout(userId: UserId, reason: LogoutReason, isHardLogout: Boolean): Either<StorageFailure, Unit>
    fun currentSession(): Either<StorageFailure, AuthSession>
    fun currentSessionFlow(): Flow<Either<StorageFailure, AuthSession>>
    fun deleteSession(userId: UserId): Either<StorageFailure, Unit>
    fun ssoId(userId: UserId): Either<StorageFailure, SsoIdEntity?>
    fun updateSsoId(userId: UserId, ssoId: SsoId?): Either<StorageFailure, Unit>
}

@Suppress("TooManyFunctions")
internal class SessionDataSource(
    private val sessionStorage: SessionStorage,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : SessionRepository {

    override fun storeSession(autSession: AuthSession, ssoId: SsoId?): Either<StorageFailure, Unit> =
        wrapStorageRequest { sessionStorage.addOrReplaceSession(sessionMapper.toPersistenceSession(autSession, ssoId)) }

    override fun updateTokens(
        userId: UserId,
        accessTokenDTO: AccessTokenDTO,
        refreshTokenDTO: RefreshTokenDTO?
    ): Either<StorageFailure, AuthSession?> =
        wrapStorageRequest { sessionStorage.userSession(idMapper.toDaoModel(userId)) }.flatMap { oldSession ->
            when (oldSession) {
                is AuthSessionEntity.Invalid -> Either.Right(null)
                is AuthSessionEntity.Valid -> {
                    wrapStorageRequest {
                        sessionStorage.addOrReplaceSession(
                            AuthSessionEntity.Valid(
                                userId = oldSession.userId,
                                tokenType = accessTokenDTO.tokenType,
                                accessToken = accessTokenDTO.value,
                                refreshToken = refreshTokenDTO?.value ?: oldSession.refreshToken,
                                oldSession.serverLinks,
                                ssoId = oldSession.ssoId
                            )
                        )
                    }.flatMap { userSession(userId) }
                }
            }
        }

    override fun allSessions(): Either<StorageFailure, List<AuthSession>> =
        wrapStorageRequest { sessionStorage.allSessions()?.values?.toList()?.map { sessionMapper.fromPersistenceSession(it) } }

    override fun allSessionsFlow(): Flow<List<AuthSession>> =
        sessionStorage.allSessionsFlow()
            .map { it.values.toList().map { sessionMapper.fromPersistenceSession(it) } }

    override fun allValidSessions(): Either<StorageFailure, List<AuthSession>> =
        wrapStorageRequest {
            sessionStorage.allSessions()?.filter { it.value is AuthSessionEntity.Valid }?.values?.toList()
                ?.map { sessionMapper.fromPersistenceSession(it) }
        }

    override fun allValidSessionsFlow(): Flow<List<AuthSession>> =
        sessionStorage.allSessionsFlow()
            .map {
                it.values.filterIsInstance<AuthSessionEntity.Valid>().toList().map { sessionMapper.fromPersistenceSession(it) }
            }

    override fun userSession(userId: UserId): Either<StorageFailure, AuthSession> =
        idMapper.toDaoModel(userId).let { userIdEntity ->
            wrapStorageRequest { sessionStorage.userSession(userIdEntity) }
                .map { sessionMapper.fromPersistenceSession(it) }
        }

    override fun doesSessionExist(userId: UserId): Either<StorageFailure, Boolean> = allSessions().fold(
        {
            when (it) {
                StorageFailure.DataNotFound -> Either.Right(false)
                is StorageFailure.Generic -> Either.Left(it)
            }
        }, { sessionsList ->
            sessionsList.forEach {
                if (it.token.userId == userId) {
                    return@fold Either.Right(true)
                }
            }
            Either.Right(false)
        })

    override fun updateCurrentSession(userId: UserId?): Either<StorageFailure, Unit> {
        val userIdEntity: UserIDEntity? = userId?.let { idMapper.toDaoModel(it) }
        return wrapStorageRequest { sessionStorage.setCurrentSession(userIdEntity) }
    }


    override fun logout(userId: UserId, reason: LogoutReason, isHardLogout: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            sessionStorage.userSession(idMapper.toDaoModel(userId))
        }.flatMap { existSession ->
            wrapStorageRequest {
                sessionStorage.addOrReplaceSession(
                    AuthSessionEntity.Invalid(
                        idMapper.toDaoModel(userId),
                        serverLinks = existSession.serverLinks,
                        reason = com.wire.kalium.persistence.model.LogoutReason.values()[reason.ordinal],
                        hardLogout = isHardLogout,
                        ssoId = existSession.ssoId,
                        tokenType = existSession.tokenType,
                        refreshToken = existSession.refreshToken,
                        accessToken = existSession.accessToken
                    )
                )
            }
        }

    override fun currentSession(): Either<StorageFailure, AuthSession> =
        wrapStorageRequest { sessionStorage.currentSession() }.map { sessionMapper.fromPersistenceSession(it) }

    override fun currentSessionFlow(): Flow<Either<StorageFailure, AuthSession>> =
        sessionStorage.currentSessionFlow()
            .wrapStorageRequest()
            .map { it.map { sessionMapper.fromPersistenceSession(it) } }

    override fun deleteSession(userId: UserId): Either<StorageFailure, Unit> =
        idMapper.toDaoModel(userId).let { userIdEntity ->
            wrapStorageRequest { sessionStorage.deleteSession(userIdEntity) }
        }

    override fun ssoId(userId: UserId): Either<StorageFailure, SsoIdEntity?> =
        wrapStorageRequest {
            sessionStorage.userSession(idMapper.toDaoModel(userId))
        }.map { it.ssoId }

    override fun updateSsoId(userId: UserId, ssoId: SsoId?): Either<StorageFailure, Unit> = wrapStorageRequest {
        sessionStorage.updateSsoId(idMapper.toDaoModel(userId), idMapper.toSsoIdEntity(ssoId))
    }

}
