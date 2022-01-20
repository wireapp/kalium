package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.PersistenceSession
import kotlinx.coroutines.flow.Flow

expect class SessionLocalDataSource {
    suspend fun addSession(persistenceSession: PersistenceSession)
    suspend fun deleteSession(userId: String)
    suspend fun currentSession(): PersistenceSession?
    suspend fun updateCurrentSession(persistenceSession: PersistenceSession)
    fun allSessions(): Flow<DataStoreResult<Map<String, PersistenceSession>>>
    suspend fun existSessions(): Boolean
}
