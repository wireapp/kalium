package com.wire.kalium.persistence.client

import androidx.datastore.preferences.core.stringPreferencesKey
import com.wire.kalium.persistence.data_store.DataStoreStorage
import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.SessionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

actual class SessionLocalDataSource(
    private val dataStore: DataStoreStorage
) {
    actual suspend fun addSession(sessionDao: SessionDao) {
        allSessions().collect { result ->
            when (result) {
                is DataStoreResult.Success -> {
                    if (result.data.containsKey(sessionDao.userId).not()) {
                        result.data.toMutableMap().put(sessionDao.userId, sessionDao)
                        saveSessions(result.data)
                        return@collect
                    }
                }
                is DataStoreResult.DataNotFound -> return@collect
            }
        }
    }

    actual suspend fun updateCurrentSession(sessionDao: SessionDao) = dataStore.setString(sessionDao.userId, CURRENT_SESSION_KEY)


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

    actual suspend fun currentSession(): SessionDao? {
        var currentSessionDao: SessionDao? = null
        dataStore.getString(CURRENT_SESSION_KEY).collect { currentUserId ->
            allSessions().collect { result ->
                currentSessionDao = when (result) {
                    is DataStoreResult.Success -> result.data[currentUserId]
                    is DataStoreResult.DataNotFound -> null
                }
            }
        }
        return currentSessionDao
    }


    actual suspend fun existSessions(): Boolean = dataStore.hasKey(stringPreferencesKey(SESSION_DATASTORE_KEY))

    actual fun allSessions(): Flow<DataStoreResult<Map<String, SessionDao>>> {
        return dataStore.getSecuredData(key = SESSION_DATASTORE_KEY, securityKeyAlias = SESSION_ENC_KEY_ALIAS)
    }

    private suspend fun saveSessions(sessions: Map<String, SessionDao>) {
        dataStore.setSecuredData(sessions, SESSION_DATASTORE_KEY, SESSION_ENC_KEY_ALIAS)
    }

    private companion object {
        private const val SESSION_DATASTORE_KEY = "session_data_store_key"
        private const val SESSION_ENC_KEY_ALIAS = "session_store_key"
        private const val CURRENT_SESSION_KEY = "current_session_key"
    }
}
