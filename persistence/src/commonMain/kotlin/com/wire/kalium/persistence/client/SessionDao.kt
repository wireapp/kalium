package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.PersistenceSession
import kotlinx.coroutines.flow.Flow

interface SessionDao {
    /**
     * store a session locally
     */
    suspend fun addSession(persistenceSession: PersistenceSession)

    /**
     * delete a session from the local storage
     */
    suspend fun deleteSession(userId: String)

    /**
     * returns the current active user session
     */
    suspend fun currentSession(): PersistenceSession?

    /**
     * changes the current active user session
     */
    suspend fun updateCurrentSession(persistenceSession: PersistenceSession)

    /**
     * return a flow with all stored session as a userId to session map
     */
    fun allSessionsFlow(): Flow<DataStoreResult<Map<String, PersistenceSession>>>

    /**
     * return all stored session as a userId to session map
     */
    suspend fun allSessions(): DataStoreResult<Map<String, PersistenceSession>>

    /**
     * returns true if there is any session saved and false otherwise
     */
    suspend fun existSessions(): Boolean
}

expect class SessionDaoImpl: SessionDao
