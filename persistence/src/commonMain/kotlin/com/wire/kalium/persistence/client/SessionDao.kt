package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.PersistenceSession

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
    suspend fun updateCurrentSession(userId: String)

    /**
     * return all stored session as a userId to session map
     */
    suspend fun allSessions(): DataStoreResult<Map<String, PersistenceSession>>

    /**
     * returns true if there is any session saved and false otherwise
     */
    suspend fun existSessions(): Boolean
}

class SessionDAOImpl(
    private val kaliumPreferences: KaliumPreferences
) : SessionDao {
    override suspend fun addSession(persistenceSession: PersistenceSession) =
        kaliumPreferences.putSerializable(SESSIONS_KEY, persistenceSession)

    override suspend fun deleteSession(userId: String) {
        when (val result = allSessions()) {
            is DataStoreResult.Success -> {
                // save the new map if the remove did not return null (session was deleted)
                result.data.toMutableMap().remove(userId)?.let {
                    saveAllSessions(result.data)
                } ?: run {
                    // session didn't exist in the first place
                }
            }
            is DataStoreResult.DataNotFound -> TODO()
        }
    }

    override suspend fun currentSession(): PersistenceSession? =
        kaliumPreferences.getString(CURRENT_SESSION_KEY)?.let { userId ->
            kaliumPreferences.getSerializable<Map<String, PersistenceSession>>(SESSIONS_KEY)?.let { storedSessions ->
                storedSessions[userId]
            }
        }


    override suspend fun updateCurrentSession(userId: String) = kaliumPreferences.putString(CURRENT_SESSION_KEY, userId)

    override suspend fun allSessions(): DataStoreResult<Map<String, PersistenceSession>> {
        return kaliumPreferences.getSerializable<Map<String, PersistenceSession>>(SESSIONS_KEY)?.let {
            DataStoreResult.Success(it)
        } ?: run { DataStoreResult.DataNotFound }
    }

    override suspend fun existSessions(): Boolean = kaliumPreferences.exitsValue(SESSIONS_KEY)

    private fun saveAllSessions(sessions: Map<String, PersistenceSession>) {
        kaliumPreferences.putSerializable(SESSIONS_KEY, sessions)
    }

    private companion object {
        private const val SESSIONS_KEY = "session_data_store_key"
        private const val CURRENT_SESSION_KEY = "current_session_key"
    }
}
