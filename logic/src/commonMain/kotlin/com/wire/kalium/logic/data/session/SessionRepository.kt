package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.client.SessionStorage

interface SessionRepository {
    fun storeSession(autSession: AuthSession): Either<StorageFailure, Unit>

    // TODO: exposing all session is unnecessary since we only need the IDs of the users getAllSessions(): Either<SessionFailure, List<UserIDs>>
    fun allSessions(): Either<StorageFailure, List<AuthSession>>
    fun userSession(userIdValue: String): Either<StorageFailure, AuthSession>
    fun doesSessionExist(userIdValue: String): Either<CoreFailure, Boolean>
    fun updateCurrentSession(userIdValue: String): Either<StorageFailure, Unit>
    fun currentSession(): Either<StorageFailure, AuthSession>
    fun deleteSession(userIdValue: String): Either<StorageFailure, Unit>
}

internal class SessionDataSource(
    private val sessionStorage: SessionStorage, private val sessionMapper: SessionMapper = MapperProvider.sessionMapper()
) : SessionRepository {
    override fun storeSession(autSession: AuthSession): Either<StorageFailure, Unit> =
        wrapStorageRequest { sessionStorage.addSession(sessionMapper.toPersistenceSession(autSession)) }

    override fun allSessions(): Either<StorageFailure, List<AuthSession>> =
        wrapStorageRequest { sessionStorage.allSessions()?.values?.toList()?.map { sessionMapper.fromPersistenceSession(it) } }


    override fun userSession(userIdValue: String): Either<StorageFailure, AuthSession> =
        wrapStorageRequest { sessionStorage.userSession(userIdValue) }.map { sessionMapper.fromPersistenceSession(it) }


    override fun doesSessionExist(userIdValue: String): Either<CoreFailure, Boolean> = allSessions().flatMap { sessionsList ->
        sessionsList.forEach {
            if (it.userId == userIdValue) {
                Either.Right(true)
            }
        }
        Either.Right(false)
    }


    override fun updateCurrentSession(userIdValue: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { sessionStorage.setCurrentSession(userIdValue) }

    override fun currentSession(): Either<StorageFailure, AuthSession> =
        wrapStorageRequest { sessionStorage.currentSession() }.map { sessionMapper.fromPersistenceSession(it) }


    override fun deleteSession(userIdValue: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { sessionStorage.deleteSession(userIdValue) }
}


