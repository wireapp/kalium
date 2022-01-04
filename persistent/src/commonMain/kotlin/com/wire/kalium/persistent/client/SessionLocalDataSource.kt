package com.wire.kalium.persistent.client

import com.wire.kalium.persistent.model.Session
import kotlinx.coroutines.flow.Flow

expect class SessionLocalDataSource {
    suspend fun addSession(session: Session)
    suspend fun deleteSession(userId: String)
    suspend fun currentSession(): Session?
    suspend fun updateCurrentSession(session: Session)
    fun allSessions(): Flow<Map<String, Session>>
    suspend fun existSessions(): Boolean
}
