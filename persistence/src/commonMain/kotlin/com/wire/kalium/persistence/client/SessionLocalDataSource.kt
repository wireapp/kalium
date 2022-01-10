package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.SessionDao
import kotlinx.coroutines.flow.Flow

expect class SessionLocalDataSource {
    suspend fun addSession(sessionDao: SessionDao)
    suspend fun deleteSession(userId: String)
    suspend fun currentSession(): SessionDao?
    suspend fun updateCurrentSession(sessionDao: SessionDao)
    fun allSessions(): Flow<DataStoreResult<Map<String, SessionDao>>>
    suspend fun existSessions(): Boolean
}
