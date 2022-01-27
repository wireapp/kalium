package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.PersistenceSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Dummy in-memory storage for CLI
 */
actual class SessionLocalDataSource {
    private val cache = hashMapOf<String, PersistenceSession>()
    private var currentSession: PersistenceSession? = null

    actual suspend fun addSession(persistenceSession: PersistenceSession) {
        cache[persistenceSession.userId] = persistenceSession
    }

    actual suspend fun deleteSession(userId: String) {
        cache.remove(userId)
    }

    actual suspend fun currentSession(): PersistenceSession? = currentSession

    actual suspend fun updateCurrentSession(persistenceSession: PersistenceSession) {
        currentSession = persistenceSession
    }

    actual suspend fun existSessions(): Boolean = cache.isNotEmpty()

    actual fun allSessions(): Flow<DataStoreResult<Map<String, PersistenceSession>>> = flow {
        if (cache.isEmpty()) {
            emit(DataStoreResult.DataNotFound)
        } else {
            emit(DataStoreResult.Success(cache))
        }
    }
}
