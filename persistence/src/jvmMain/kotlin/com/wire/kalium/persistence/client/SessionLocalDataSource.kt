package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.SessionDao
import kotlinx.coroutines.flow.Flow

actual class SessionLocalDataSource {
    actual suspend fun addSession(sessionDao: SessionDao) {
        TODO("Not yet implemented")
    }

    actual suspend fun deleteSession(userId: String) {
        TODO("Not yet implemented")
    }

    actual suspend fun currentSession(): SessionDao? {
        TODO("Not yet implemented")
    }

    actual suspend fun updateCurrentSession(sessionDao: SessionDao) {
        TODO("Not yet implemented")
    }

    actual suspend fun existSessions(): Boolean {
        TODO("Not yet implemented")
    }

    actual fun allSessions(): Flow<DataStoreResult<Map<String, SessionDao>>> {
        TODO("Not yet implemented")
    }
}
