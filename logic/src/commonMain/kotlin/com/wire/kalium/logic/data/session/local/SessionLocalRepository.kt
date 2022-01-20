package com.wire.kalium.logic.data.session.local

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.failure.SessionFailure
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.client.SessionDao
import com.wire.kalium.persistence.model.DataStoreResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SessionLocalRepository {
    suspend fun storeSession(autSession: AuthSession)
    suspend fun getSessions(): Either<CoreFailure, List<AuthSession>>
}

class SessionLocalDataSource(
    private val sessionLocalDataSource: SessionDao,
    private val sessionMapper: SessionMapper
) : SessionLocalRepository {
    override suspend fun storeSession(autSession: AuthSession) =
        sessionLocalDataSource.addSession(sessionMapper.toPersistenceSession(autSession))


    override suspend fun getSessions(): Either<CoreFailure, List<AuthSession>> =
        when (val result = sessionLocalDataSource.allSessions()) {
            is DataStoreResult.Success -> Either.Right(
                result.data.values.toList().map { sessionMapper.fromPersistenceSession(it) })

            is DataStoreResult.DataNotFound -> Either.Left(SessionFailure.NoSessionFound)
        }

}
