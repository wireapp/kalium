package com.wire.kalium.persistence.client

import androidx.datastore.preferences.core.stringPreferencesKey
import com.wire.kalium.persistence.data_store.DataStoreStorage
import com.wire.kalium.persistence.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

actual class SessionLocalDataSource(
    private val dataStore: DataStoreStorage
) {
    actual suspend fun addSession(session: Session) {
        allSessions().collect { sessions ->
            if (sessions.containsKey(session.userId).not()) {
                sessions.toMutableMap().put(session.userId, session)
                saveSessions(sessions)
                return@collect
            }
        }
    }

    actual suspend fun updateCurrentSession(session: Session) = dataStore.setString(session.userId, CURRENT_SESSION_KEY)


    actual suspend fun deleteSession(userId: String) {
        allSessions().collect { sessions ->
            sessions.toMutableMap().remove(userId)
            saveSessions(sessions)
            return@collect
        }
    }

    actual suspend fun currentSession(): Session? {
        var currentSession: Session? = null
        dataStore.getString(CURRENT_SESSION_KEY).collect { currentUserId ->
            allSessions().collect {
                currentSession = it[currentUserId]
            }
        }
        return currentSession
    }

    actual fun allSessions(): Flow<Map<String, Session>> {
        return dataStore.getSecuredData(key = SESSION_DATASTORE_KEY, securityKeyAlias = SESSION_ENC_KEY_ALIAS)
    }

    actual suspend fun existSessions(): Boolean = dataStore.hasKey(stringPreferencesKey(SESSION_DATASTORE_KEY))


    private suspend fun saveSessions(sessions: Map<String, Session>) {
        dataStore.setSecuredData(sessions, SESSION_DATASTORE_KEY, SESSION_ENC_KEY_ALIAS)
    }

    private companion object {
        private const val SESSION_DATASTORE_KEY = "session_data_store_key"
        private const val SESSION_ENC_KEY_ALIAS = "session_store_key"
        private const val CURRENT_SESSION_KEY = "current_session_key"
    }
}
