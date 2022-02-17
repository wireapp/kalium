package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.model.PreferencesResult
import com.wire.kalium.persistence.model.PersistenceSession
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

interface SessionStorage {
    /**
     * store a session locally
     */
    fun addSession(persistenceSession: PersistenceSession)

    /**
     * delete a session from the local storage
     */
    fun deleteSession(userId: String)

    /**
     * returns the current active user session
     */
     fun currentSession(): PersistenceSession?

    /**
     * changes the current active user session
     */
    fun updateCurrentSession(userId: String)

    /**
     * return all stored session as a userId to session map
     */
    fun allSessions(): PreferencesResult<Map<String, PersistenceSession>>

    /**
     * returns true if there is any session saved and false otherwise
     */
    fun sessionsExist(): Boolean
}

class SessionStorageImpl(
    private val kaliumPreferences: KaliumPreferences
) : SessionStorage {
    override fun addSession(persistenceSession: PersistenceSession) =
        when (val result = allSessions()) {
            is PreferencesResult.Success -> {
                val temp = result.data.toMutableMap()
                temp[persistenceSession.userId] = persistenceSession
                saveAllSessions(SessionsMap(temp))
            }
            PreferencesResult.DataNotFound -> {
                val sessions = mapOf(persistenceSession.userId to persistenceSession)
                saveAllSessions(SessionsMap(sessions))
            }
        }

    override fun deleteSession(userId: String) {
        when (val result = allSessions()) {
            is PreferencesResult.Success -> {
                // save the new map if the remove did not return null (session was deleted)
                val temp = result.data.toMutableMap()
                temp.remove(userId)?.let {
                    if (temp.isEmpty()) {
                        // in case it was the last session then delete sessions key/value from the file
                        removeAllSession()
                    } else {
                        saveAllSessions(SessionsMap(temp))
                    }
                } ?: {
                    // session didn't exist in the first place
                }
            }
            is PreferencesResult.DataNotFound -> {
                // trying to delete a session when no sessions are actually stored
            }
        }
    }

    override  fun currentSession(): PersistenceSession? =
        kaliumPreferences.getString(CURRENT_SESSION_KEY)?.let { userId ->
            when (val result = allSessions()) {
                is PreferencesResult.Success -> result.data[userId]
                PreferencesResult.DataNotFound -> null
            }
        }


    override fun updateCurrentSession(userId: String) = kaliumPreferences.putString(CURRENT_SESSION_KEY, userId)

    override fun allSessions(): PreferencesResult<Map<String, PersistenceSession>> {
        return kaliumPreferences.getSerializable(SESSIONS_KEY, SessionsMap.serializer())?.let {
            if (it.s.isEmpty()) {
                // the sessions hashMap is empty
                PreferencesResult.DataNotFound
            } else {
                PreferencesResult.Success(it.s)
            }
        } ?: run { PreferencesResult.DataNotFound }
    }

    override fun sessionsExist(): Boolean = kaliumPreferences.hasValue(SESSIONS_KEY)

    private fun saveAllSessions(sessions: SessionsMap) {
        kaliumPreferences.putSerializable(SESSIONS_KEY, sessions, SessionsMap.serializer())
    }

    private fun removeAllSession() = kaliumPreferences.remove(SESSIONS_KEY)


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
