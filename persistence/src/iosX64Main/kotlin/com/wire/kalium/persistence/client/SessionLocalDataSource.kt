package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.Session
import kotlinx.coroutines.flow.Flow

actual class SessionLocalDataSource {
    actual suspend fun addSession(session: Session) {
    }

    actual suspend fun deleteSession(userId: String) {
    }

    actual suspend fun currentSession(): Session? {
        TODO("Not yet implemented")
    }

    actual suspend fun updateCurrentSession(session: Session) {
    }

    actual fun allSessions(): Flow<DataStoreResult<Map<String, Session>>> {
        TODO("Not yet implemented")
    }

    actual suspend fun existSessions(): Boolean {
        TODO("Not yet implemented")
    }
}
