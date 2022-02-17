package com.wire.kalium.logic.data.session.local

import com.wire.kalium.logic.SessionFailure
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.client.SessionStorage
import com.wire.kalium.persistence.model.PreferencesResult

interface SessionLocalRepository {
    fun storeSession(autSession: AuthSession)
    fun getSessions(): Either<SessionFailure, List<AuthSession>>
    fun updateCurrentSession(userIdValue: String)
    fun getCurrentSession(): Either<SessionFailure, AuthSession>
    fun deleteSession(userIdValue: String)
}

class SessionLocalDataSource(
    private val sessionStorage: SessionStorage,
    private val sessionMapper: SessionMapper
) : SessionLocalRepository {

    override fun storeSession(autSession: AuthSession) =
        sessionStorage.addSession(sessionMapper.toPersistenceSession(autSession))

    override fun getSessions(): Either<SessionFailure, List<AuthSession>> =
        when (val result = sessionStorage.allSessions()) {
            is PreferencesResult.Success -> Either.Right(
                result.data.values.toList().map { sessionMapper.fromPersistenceSession(it) }
            )
            is PreferencesResult.DataNotFound -> Either.Left(SessionFailure.NoSessionFound)
        }

    override fun getCurrentSession(): Either<SessionFailure, AuthSession> {
        return sessionStorage.currentSession()?.let { currentSession ->
            return@let Either.Right(sessionMapper.fromPersistenceSession(currentSession))
        } ?: run {
            return@run Either.Left(SessionFailure.NoSessionFound)
        }
    }

    override fun deleteSession(userIdValue: String) = sessionStorage.deleteSession(userIdValue)

    override fun updateCurrentSession(userIdValue: String) = sessionStorage.updateCurrentSession(userIdValue)
}
