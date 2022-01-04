package com.wire.kalium.persistent.client

import androidx.datastore.dataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wire.kalium.persistent.data_store.DataStoreStorage
import com.wire.kalium.persistent.model.Session
import com.wire.kalium.persistent.util.SecurityUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map

actual class SessionLocalDataSource(
    private val dataStore: DataStoreStorage
) {
    actual suspend fun addSession(session: Session) {
        allSessions().collectLatest { sessions ->
            if (sessions.containsKey(session.userId).not()) {
                sessions.toMutableMap().put(session.userId, session)
                saveSessions(sessions)
                return@collectLatest
            }
        }
    }

    actual suspend fun updateCurrentSession(session: Session) = dataStore.setString(session.userId, CURRENT_SESSION_KEY)


    actual suspend fun deleteSession(userId: String) {
        allSessions().collect { sessions ->
            sessions.toMutableMap().remove(userId)
            saveSessions(sessions)
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
        return dataStore.getSecuredData(key = DATASTORE_KEY, securityKeyAlias = SESSION_ENC_KEY_ALIAS)
    }

    actual suspend fun existSessions(): Boolean = dataStore.hasKey(stringPreferencesKey(DATASTORE_KEY))


    private suspend fun saveSessions(sessions: Map<String, Session>) {
        dataStore.setSecuredData(sessions, DATASTORE_KEY, SESSION_ENC_KEY_ALIAS)
    }

    private companion object {
        private const val DATASTORE_KEY = "session_data_store_key"
        private const val SESSION_ENC_KEY_ALIAS = "session_store_key"
        private const val CURRENT_SESSION_KEY = "current_session_key"
    }
}
