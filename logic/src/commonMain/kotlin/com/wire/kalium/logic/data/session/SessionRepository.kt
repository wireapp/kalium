package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.session.local.SessionLocalRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either

interface SessionRepository {
    suspend fun storeSession(autSession: AuthSession)
    suspend fun getSessions(): Either<CoreFailure, List<AuthSession>>
    suspend fun doesSessionExist(userId: UserId): Either<CoreFailure, Boolean>
}

@Deprecated("Use the SessionRepositoryImpl", replaceWith = ReplaceWith("SessionRepositoryImpl"))
class InMemorySessionRepository : SessionRepository {
    private val sessions = hashMapOf<String, AuthSession>()

    override suspend fun storeSession(autSession: AuthSession) {
        sessions[autSession.userId] = autSession
    }

    override suspend fun getSessions(): Either<CoreFailure, List<AuthSession>> = Either.Right(sessions.values.toList())
    override suspend fun doesSessionExist(userId: UserId): Either<CoreFailure, Boolean> {
        TODO("Not yet implemented")
    }

}

class SessionDataSource(
    private val sessionLocalRepository: SessionLocalRepository
) : SessionRepository {
    override suspend fun storeSession(autSession: AuthSession) = sessionLocalRepository.storeSession(autSession)

    override suspend fun getSessions(): Either<CoreFailure, List<AuthSession>> = sessionLocalRepository.getSessions()

    override suspend fun doesSessionExist(userId: UserId): Either<CoreFailure, Boolean> =
        when (val result = sessionLocalRepository.getSessions()) {
            is Either.Left -> Either.Left(result.value)

            is Either.Right -> {
                result.value.forEach {
                    if (it.userId == userId.value) {
                        Either.Right(true)
                    }
                }
                Either.Right(false)
            }
        }
}


