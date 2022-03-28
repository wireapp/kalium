package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.model.PersistenceSession
import com.wire.kalium.persistence.model.PreferencesResult
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
    fun deleteSession(userId: QualifiedIDEntity)

    /**
     * returns the current active user session
     */
    fun currentSession(): PersistenceSession?

    /**
     * changes the current active user session
     */
    fun setCurrentSession(userId: QualifiedIDEntity)

    /**
     * return all stored session as a userId to session map
     */
    fun allSessions(): PreferencesResult<Map<QualifiedIDEntity, PersistenceSession>>

    /**
     * return stored session associated with a userId
     */
    fun userSession(userId: QualifiedIDEntity): PreferencesResult<PersistenceSession>

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

    override fun deleteSession(userId: QualifiedIDEntity) {
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

    override fun currentSession(): PersistenceSession? =
        kaliumPreferences.getSerializable(CURRENT_SESSION_KEY, QualifiedIDEntity.serializer())?.let { userId ->
            when (val result = allSessions()) {
                is PreferencesResult.Success -> result.data[userId]
                PreferencesResult.DataNotFound -> null
            }
        }


    override fun setCurrentSession(userId: QualifiedIDEntity) =
        kaliumPreferences.putSerializable(CURRENT_SESSION_KEY, userId, QualifiedIDEntity.serializer())

    override fun allSessions(): PreferencesResult<Map<QualifiedIDEntity, PersistenceSession>> {
        return kaliumPreferences.getSerializable(SESSIONS_KEY, SessionsMap.serializer())?.let {
            if (it.s.isEmpty()) {
                // the sessions hashMap is empty
                PreferencesResult.DataNotFound
            } else {
                PreferencesResult.Success(it.s)
            }
        } ?: run { PreferencesResult.DataNotFound }
    }

    override fun userSession(userId: QualifiedIDEntity): PreferencesResult<PersistenceSession> = with(allSessions()) {
        when (this) {
            is PreferencesResult.Success -> this.data[userId]?.let {
                PreferencesResult.Success(it)
            } ?: run {
                PreferencesResult.DataNotFound
            }
            PreferencesResult.DataNotFound -> PreferencesResult.DataNotFound
        }
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
private value class SessionsMap(val s: Map<QualifiedIDEntity, PersistenceSession>)
