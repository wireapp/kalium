package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.feature.auth.AuthSession

interface SessionRepository {
    fun storeSession(session: AuthSession)
    fun getSessions(): List<AuthSession>
}

class InMemorySessionRepository : SessionRepository {
    private val sessions = hashMapOf<String, AuthSession>()

    override fun storeSession(session: AuthSession) {
        sessions[session.userId] = session
    }

    override fun getSessions(): List<AuthSession> = sessions.values.toList()

}
