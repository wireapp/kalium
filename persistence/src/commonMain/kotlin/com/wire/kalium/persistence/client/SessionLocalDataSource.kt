package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.model.Session
import kotlinx.coroutines.flow.Flow

expect class SessionLocalDataSource {
    suspend fun addSession(session: Session)
    suspend fun deleteSession(userId: String)
    suspend fun currentSession(): Session?
    suspend fun updateCurrentSession(session: Session)
    fun allSessions(): Flow<Map<String, Session>>
    suspend fun existSessions(): Boolean
}
