package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.failure.SessionFailure
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.client.SessionLocalDataSource
import com.wire.kalium.persistence.model.DataStoreResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

interface SessionRepository {
    suspend fun storeSession(autSession: AuthSession)
    suspend fun getSessions(): Flow<Either<CoreFailure, List<AuthSession>>>
}

@Deprecated("Use the SessionRepositoryImpl", replaceWith = ReplaceWith("SessionRepositoryImpl"))
class InMemorySessionRepository : SessionRepository {
    private val sessions = hashMapOf<String, AuthSession>()

    override suspend fun storeSession(autSession: AuthSession) {
        sessions[autSession.userId] = autSession
    }

    override suspend fun getSessions(): Flow<Either<CoreFailure, List<AuthSession>>> = flow { emit(Either.Right(sessions.values.toList())) }

}

class SessionRepositoryImpl(
    private val sessionLocalDataSource: SessionLocalDataSource,
    private val sessionMapper: SessionMapper
) : SessionRepository {
    override suspend fun storeSession(autSession: AuthSession) {
        sessionLocalDataSource.addSession(sessionMapper.toPersistenceSession(autSession))
    }

    override suspend fun getSessions(): Flow<Either<CoreFailure, List<AuthSession>>> =
        sessionLocalDataSource.allSessions().map { result ->
            when (result) {
                is DataStoreResult.Success -> return@map Either.Right(
                    result.data.values.toList().map { sessionMapper.fromPersistenceSession(it) })
                is DataStoreResult.DataNotFound -> return@map Either.Left(SessionFailure.NoSessionFound)
            }
        }
}
