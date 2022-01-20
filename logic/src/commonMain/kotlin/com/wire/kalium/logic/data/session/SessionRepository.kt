package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.session.local.SessionLocalRepository
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

interface SessionRepository {
    suspend fun storeSession(autSession: AuthSession)
    suspend fun getSessions(): Either<CoreFailure, List<AuthSession>>
    suspend fun doseSessionExist(userId: String): Either<CoreFailure, Boolean>
}

@Deprecated("Use the SessionRepositoryImpl", replaceWith = ReplaceWith("SessionRepositoryImpl"))
class InMemorySessionRepository : SessionRepository {
    private val sessions = hashMapOf<String, AuthSession>()

    override suspend fun storeSession(autSession: AuthSession) {
        sessions[autSession.userId] = autSession
    }

    override suspend fun getSessions(): Either<CoreFailure, List<AuthSession>> = Either.Right(sessions.values.toList())
    override suspend fun doseSessionExist(userId: String): Either<CoreFailure, Boolean> {
        TODO("Not yet implemented")
    }

}

class SessionDataSource(
    private val sessionLocalRepository: SessionLocalRepository
) : SessionRepository {
    override suspend fun storeSession(autSession: AuthSession) = sessionLocalRepository.storeSession(autSession)

    override suspend fun getSessions(): Either<CoreFailure, List<AuthSession>> = sessionLocalRepository.getSessions()

    override suspend fun doseSessionExist(userId: String): Either<CoreFailure, Boolean> =
        when (val sessions = sessionLocalRepository.getSessions()) {
            is Either.Left -> Either.Left(sessions.value)

            is Either.Right -> {
                sessions.value.forEach {
                    if (it.userId == userId) {
                        Either.Right(true)
                    }
                }
                Either.Right(false)
            }
        }
}


