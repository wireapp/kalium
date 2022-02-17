package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.SessionFailure
import com.wire.kalium.logic.data.session.local.SessionLocalRepository
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either

interface SessionRepository {
    fun storeSession(autSession: AuthSession)
    fun getSessions(): Either<SessionFailure, List<AuthSession>>
    fun doesSessionExist(userIdValue: String): Either<CoreFailure, Boolean>
    fun updateCurrentSession(userIdValue: String)
    fun currentSession(): Either<SessionFailure, AuthSession>
    fun deleteSession(userIdValue: String)
}

class SessionDataSource(
    private val sessionLocalRepository: SessionLocalRepository
) : SessionRepository {
    override fun storeSession(autSession: AuthSession) = sessionLocalRepository.storeSession(autSession)

    override fun getSessions(): Either<SessionFailure, List<AuthSession>> = sessionLocalRepository.getSessions()

    override fun doesSessionExist(userIdValue: String): Either<CoreFailure, Boolean> =
        when (val result = sessionLocalRepository.getSessions()) {
            is Either.Left -> Either.Left(result.value)

            is Either.Right -> {
                result.value.forEach {
                    if (it.userId == userIdValue) {
                        Either.Right(true)
                    }
                }
                Either.Right(false)
            }
        }

    override fun updateCurrentSession(userIdValue: String) = sessionLocalRepository.updateCurrentSession(userIdValue)

    override fun currentSession(): Either<SessionFailure, AuthSession> = sessionLocalRepository.getCurrentSession()

    override fun deleteSession(userIdValue: String) = sessionLocalRepository.deleteSession(userIdValue)
}


