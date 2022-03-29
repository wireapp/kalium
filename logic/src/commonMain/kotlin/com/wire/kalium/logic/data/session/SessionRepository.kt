package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.SessionFailure
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.persistence.client.SessionStorage
import com.wire.kalium.persistence.model.PreferencesResult

interface SessionRepository {
    fun storeSession(autSession: AuthSession)

    // TODO: exposing all session is unnecessary since we only need the IDs of the users getAllSessions(): Either<SessionFailure, List<UserIDs>>
    fun allSessions(): Either<SessionFailure, List<AuthSession>>
    fun userSession(userId: UserId): Either<SessionFailure, AuthSession>
    fun doesSessionExist(userId: UserId): Either<CoreFailure, Boolean>
    fun updateCurrentSession(userId: UserId)
    fun currentSession(): Either<SessionFailure, AuthSession>
    fun deleteSession(userId: UserId)
}

internal class SessionDataSource(
    private val sessionStorage: SessionStorage,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : SessionRepository {
    override fun storeSession(autSession: AuthSession) = sessionStorage.addSession(sessionMapper.toPersistenceSession(autSession))

    override fun allSessions(): Either<SessionFailure, List<AuthSession>> =
        when (val result = sessionStorage.allSessions()) {
            is PreferencesResult.Success -> Either.Right(result.data.values.toList().map { sessionMapper.fromPersistenceSession(it) })
            is PreferencesResult.DataNotFound -> Either.Left(SessionFailure.NoSessionFound)
        }

    override fun userSession(userId: UserId): Either<SessionFailure, AuthSession> =
        idMapper.toDaoModel(userId).let { userIdEntity ->
            when (val result = sessionStorage.userSession(userIdEntity)) {
                is PreferencesResult.Success -> Either.Right(sessionMapper.fromPersistenceSession(result.data))
                PreferencesResult.DataNotFound -> Either.Left(SessionFailure.NoSessionFound)
            }
        }


    override fun doesSessionExist(userId: UserId): Either<CoreFailure, Boolean> = allSessions().flatMap { sessionsList ->
        sessionsList.forEach {
            if (it.userId == userId) {
                Either.Right(true)
            }
        }
        Either.Right(false)
    }


    override fun updateCurrentSession(userId: UserId) = sessionStorage.setCurrentSession(idMapper.toDaoModel(userId))

    override fun currentSession(): Either<SessionFailure, AuthSession> = sessionStorage.currentSession()?.let { currentSession ->
        return@let Either.Right(sessionMapper.fromPersistenceSession(currentSession))
    } ?: run {
        return@run Either.Left(SessionFailure.NoSessionFound)
    }

    override fun deleteSession(userId: UserId) = sessionStorage.deleteSession(idMapper.toDaoModel(userId))
}


