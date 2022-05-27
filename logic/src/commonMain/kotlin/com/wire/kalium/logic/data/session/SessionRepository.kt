package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.client.SessionStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SessionRepository {
    fun storeSession(autSession: AuthSession): Either<StorageFailure, Unit>

    // TODO(optimization): exposing all session is unnecessary since we only need the IDs
    //                     of the users getAllSessions(): Either<SessionFailure, List<UserIDs>>
    fun allSessions(): Either<StorageFailure, List<AuthSession>>
    fun userSession(userId: UserId): Either<StorageFailure, AuthSession>
    fun doesSessionExist(userId: UserId): Either<StorageFailure, Boolean>
    fun updateCurrentSession(userId: UserId): Either<StorageFailure, Unit>
    fun currentSession(): Either<StorageFailure, AuthSession>
    fun currentSessionFlow(): Flow<Either<StorageFailure, AuthSession>>
    fun deleteSession(userId: UserId): Either<StorageFailure, Unit>
}

internal class SessionDataSource(
    private val sessionStorage: SessionStorage,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : SessionRepository {

    override fun storeSession(autSession: AuthSession): Either<StorageFailure, Unit> =
        wrapStorageRequest { sessionStorage.addSession(sessionMapper.toPersistenceSession(autSession)) }

    override fun allSessions(): Either<StorageFailure, List<AuthSession>> =
        wrapStorageRequest { sessionStorage.allSessions()?.values?.toList()?.map { sessionMapper.fromPersistenceSession(it) } }

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
                if (it.userId == userId) {
                    return@fold Either.Right(true)
                }
            }
            Either.Right(false)
        })

    override fun updateCurrentSession(userId: UserId): Either<StorageFailure, Unit> =
        idMapper.toDaoModel(userId).let { userIdEntity ->
            wrapStorageRequest { sessionStorage.setCurrentSession(userIdEntity) }
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
}
