package com.wire.kalium.persistence.client

import androidx.datastore.preferences.core.stringPreferencesKey
import com.wire.kalium.persistence.data_store.DataStoreStorage
import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.PersistenceSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

actual class SessionLocalDataSource(
    private val dataStore: DataStoreStorage
) {
    actual suspend fun addSession(persistenceSession: PersistenceSession) {
        allSessions().collect { result ->
            when (result) {
                is DataStoreResult.Success -> {
                    if (result.data.containsKey(persistenceSession.userId).not()) {
                        result.data.toMutableMap().put(persistenceSession.userId, persistenceSession)
                        saveSessions(result.data)
                        return@collect
                    }
                }
                is DataStoreResult.DataNotFound -> return@collect
            }
        }
    }

    actual suspend fun updateCurrentSession(persistenceSession: PersistenceSession) =
        dataStore.setString(persistenceSession.userId, CURRENT_SESSION_KEY)


    actual suspend fun deleteSession(userId: String) {
        allSessions().collect { result ->
            when (result) {
                is DataStoreResult.Success -> {
                    result.data.toMutableMap().remove(userId)
                    saveSessions(result.data)
                    return@collect
                }
                is DataStoreResult.DataNotFound -> return@collect
            }
        }
    }

    actual suspend fun currentSession(): PersistenceSession? {
        var currentPersistenceSession: PersistenceSession? = null
        dataStore.getString(CURRENT_SESSION_KEY).collect { currentUserId ->
            allSessions().collect { result ->
                currentPersistenceSession = when (result) {
                    is DataStoreResult.Success -> result.data[currentUserId]
                    is DataStoreResult.DataNotFound -> null
                }
            }
        }
        return currentPersistenceSession
    }


    actual suspend fun existSessions(): Boolean = dataStore.hasKey(stringPreferencesKey(SESSION_DATASTORE_KEY))

    actual fun allSessions(): Flow<DataStoreResult<Map<String, PersistenceSession>>> {
        return dataStore.getSecuredData(key = SESSION_DATASTORE_KEY, securityKeyAlias = SESSION_ENC_KEY_ALIAS)
    }

    private suspend fun saveSessions(sessions: Map<String, PersistenceSession>) {
        dataStore.setSecuredData(sessions, SESSION_DATASTORE_KEY, SESSION_ENC_KEY_ALIAS)
    }

    private companion object {
        private const val SESSION_DATASTORE_KEY = "session_data_store_key"
        private const val SESSION_ENC_KEY_ALIAS = "session_store_key"
        private const val CURRENT_SESSION_KEY = "current_session_key"
    }
}
