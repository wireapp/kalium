package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.PersistenceSession
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

interface SessionDAO {
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
) : SessionDAO {
    override suspend fun addSession(persistenceSession: PersistenceSession) =
        when(val result = allSessions()) {
            is DataStoreResult.Success -> {
                result.data.toMutableMap()[persistenceSession.userId] = persistenceSession
                saveAllSessions(SessionsMap(result.data))
            }
            DataStoreResult.DataNotFound -> {
                val sessions = mapOf(persistenceSession.userId to persistenceSession)
                saveAllSessions(SessionsMap(sessions))
            }
        }

    override suspend fun deleteSession(userId: String) {
        when (val result = allSessions()) {
            is DataStoreResult.Success -> {
                // save the new map if the remove did not return null (session was deleted)
                result.data.toMutableMap().remove(userId)?.let {
                    saveAllSessions(SessionsMap(result.data))
                } ?: run {
                    // session didn't exist in the first place
                }
            }
            is DataStoreResult.DataNotFound -> TODO()
        }
    }

    override suspend fun currentSession(): PersistenceSession? =
        kaliumPreferences.getString(CURRENT_SESSION_KEY)?.let { userId ->
            when (val result = allSessions()) {
                is DataStoreResult.Success -> result.data[userId]
                DataStoreResult.DataNotFound -> null
            }
        }


    override suspend fun updateCurrentSession(userId: String) = kaliumPreferences.putString(CURRENT_SESSION_KEY, userId)

    override suspend fun allSessions(): DataStoreResult<Map<String, PersistenceSession>> {
        return kaliumPreferences.getSerializable(SESSIONS_KEY, SessionsMap.serializer())?.let {
            DataStoreResult.Success(it.s)
        } ?: run { DataStoreResult.DataNotFound }
    }

    override suspend fun existSessions(): Boolean = kaliumPreferences.exitsValue(SESSIONS_KEY)

    private fun saveAllSessions(sessions: SessionsMap) {
        kaliumPreferences.putSerializable(SESSIONS_KEY, sessions, SessionsMap.serializer())
    }

    private companion object {
        private const val SESSIONS_KEY = "session_data_store_key"
        private const val CURRENT_SESSION_KEY = "current_session_key"
    }
}

// No actual instantiation of class 'SessionsMap' happens
// At runtime an object of 'SessionsMap' contains just 'Map<String, PersistenceSession>'
@Serializable
@JvmInline
private value class SessionsMap(val s: Map<String, PersistenceSession>)
