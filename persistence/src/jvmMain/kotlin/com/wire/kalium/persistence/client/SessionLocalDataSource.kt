package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.model.Session
import kotlinx.coroutines.flow.Flow

actual class SessionLocalDataSource {
    actual suspend fun addSession(session: Session) {
        TODO("Not yet implemented")
    }

    actual suspend fun deleteSession(userId: String) {
        TODO("Not yet implemented")
    }

    actual suspend fun currentSession(): Session? {
        TODO("Not yet implemented")
    }

    actual suspend fun updateCurrentSession(session: Session) {
        TODO("Not yet implemented")
    }

    actual fun allSessions(): Flow<Map<String, Session>> {
        TODO("Not yet implemented")
    }

    actual suspend fun existSessions(): Boolean {
        TODO("Not yet implemented")
    }
}
