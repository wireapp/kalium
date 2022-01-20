package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.PersistenceSession
import kotlinx.coroutines.flow.Flow

actual class SessionLocalDataSource {
    actual suspend fun addSession(persistenceSession: PersistenceSession) {
    }

    actual suspend fun deleteSession(userId: String) {
    }

    actual suspend fun currentSession(): PersistenceSession? {
        TODO("Not yet implemented")
    }

    actual suspend fun updateCurrentSession(persistenceSession: PersistenceSession) {
    }

    actual fun allSessions(): Flow<DataStoreResult<Map<String, PersistenceSession>>> {
        TODO("Not yet implemented")
    }

    actual suspend fun existSessions(): Boolean {
        TODO("Not yet implemented")
    }
}
